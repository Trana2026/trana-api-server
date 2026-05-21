package com.trana.identity.adapter.ncp

import com.trana.identity.IdentityException
import com.trana.identity.adapter.IdCardOcrAdapter
import com.trana.identity.adapter.IdCardOcrOutput
import com.trana.identity.adapter.IdCardRecognitionResult
import com.trana.identity.adapter.IdCardSensitiveData
import com.trana.identity.adapter.IdType
import com.trana.identity.adapter.ImageFormat
import com.trana.identity.adapter.ImageInput
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * NCP CLOVA eKYC Document API 어댑터.
 *
 * 신분증 이미지 → IdCardOcrOutput (외부 result + 내부 sensitive).
 * 3종 지원: 주민등록증(ic) / 운전면허증(dl) / 외국인등록증(ac).
 */
@Component
@Suppress("TooManyFunctions")
class NcpIdCardAdapter(
    props: NcpEkycProperties,
    private val objectMapper: ObjectMapper,
) : IdCardOcrAdapter {
    private val client: RestClient =
        RestClient
            .builder()
            .baseUrl(props.idCard.invokeUrl)
            .defaultHeader(EKYC_SECRET_HEADER, props.idCard.secretKey)
            .build()

    override fun recognizeIdCard(image: ImageInput): IdCardOcrOutput {
        val response = callDocumentApi(image)
        return parseDocumentResponse(response)
    }

    private fun callDocumentApi(image: ImageInput): NcpDocumentResponse {
        val body = buildDocumentMultipart(image)
        return try {
            client
                .post()
                .uri("/document")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body<NcpDocumentResponse>()
                ?: throw IdentityException.NcpCallFailed(
                    "Document",
                    IllegalStateException("empty response"),
                )
        } catch (e: RestClientException) {
            throw IdentityException.NcpCallFailed("Document", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun parseDocumentResponse(response: NcpDocumentResponse): IdCardOcrOutput =
        try {
            mapToOutput(response)
        } catch (e: IdentityException) {
            throw e
        } catch (e: RuntimeException) {
            throw IdentityException.OcrRejected(e.message ?: "OCR 결과 파싱 실패", e)
        }

    private fun buildDocumentMultipart(image: ImageInput): MultiValueMap<String, HttpEntity<*>> {
        val body = LinkedMultiValueMap<String, HttpEntity<*>>()

        val messageJson =
            mapOf(
                "version" to "V2",
                "requestId" to UUID.randomUUID().toString(),
                "timestamp" to Instant.now().toEpochMilli(),
                "images" to
                    listOf(
                        mapOf("name" to "id-card", "format" to image.format.name.lowercase()),
                    ),
            )
        val messageHeaders = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        body.add("message", HttpEntity(objectMapper.writeValueAsString(messageJson), messageHeaders))

        val fileResource =
            object : ByteArrayResource(image.bytes) {
                override fun getFilename(): String = image.originalFilename
            }
        val fileHeaders =
            HttpHeaders().apply {
                contentType =
                    if (image.format == ImageFormat.PNG) MediaType.IMAGE_PNG else MediaType.IMAGE_JPEG
            }
        body.add("file", HttpEntity(fileResource, fileHeaders))

        return body
    }

    private fun mapToOutput(response: NcpDocumentResponse): IdCardOcrOutput {
        val requestId = response.requestId ?: error("Document API 응답에 requestId 없음")
        val image =
            response.images.firstOrNull()
                ?: error("Document API 응답에 images가 비어 있음")
        check(image.inferResult == "SUCCESS") { "Document API 인식 실패: ${image.message}" }

        val idCard = image.idCard?.result ?: error("idCard 결과 없음")
        val confidence = idCard.isConfident ?: false
        val faceImage =
            image.face
                ?.faces
                ?.firstOrNull()
                ?.alignedImage

        return when (mapIdType(idCard.idtype)) {
            IdType.ID_CARD -> {
                mapResidentIdCard(requestId, idCard.ic ?: error("ic 정보 없음"), confidence, faceImage)
            }

            IdType.DRIVER_LICENSE -> {
                mapDriverLicense(requestId, idCard.dl ?: error("dl 정보 없음"), confidence, faceImage)
            }

            IdType.ALIEN_REGISTRATION -> {
                mapAlienRegistration(requestId, idCard.ac ?: error("ac 정보 없음"), confidence, faceImage)
            }
        }
    }

    private fun mapResidentIdCard(
        requestId: String,
        sub: NcpIdCardSubject,
        confidence: Boolean,
        faceImage: String?,
    ): IdCardOcrOutput {
        val base =
            extractBaseFields(
                rrnRaw = sub.personalNum.firstTextOrError("주민번호"),
                nameTexts = sub.name,
                issueDateTexts = sub.issueDate,
                idTypeLabel = "주민등록증",
            )
        val result =
            IdCardRecognitionResult.ResidentIdCard(
                name = base.name,
                birthDate = base.parsed.birthDate,
                gender = base.parsed.gender,
                issueDate = base.issueDate,
                rawConfidence = confidence,
                faceImageBase64 = faceImage,
                personalNumberHash = base.parsed.hash,
                address = sub.address.firstText(),
            )
        val sensitive =
            IdCardSensitiveData(
                requestId = requestId,
                name = base.name,
                personalNumber = base.rrnSanitized,
                issueDate = base.issueDate,
            )
        return IdCardOcrOutput(result, sensitive)
    }

    private fun mapDriverLicense(
        requestId: String,
        sub: NcpIdCardSubject,
        confidence: Boolean,
        faceImage: String?,
    ): IdCardOcrOutput {
        val base =
            extractBaseFields(
                rrnRaw = sub.personalNum.firstTextOrError("주민번호"),
                nameTexts = sub.name,
                issueDateTexts = sub.issueDate,
                idTypeLabel = "운전면허증",
            )
        val licenseNumber = sub.num.firstTextOrError("면허번호")
        val licenseSecurityCode = sub.code.firstText()

        val result =
            IdCardRecognitionResult.DriverLicense(
                name = base.name,
                birthDate = base.parsed.birthDate,
                gender = base.parsed.gender,
                issueDate = base.issueDate,
                rawConfidence = confidence,
                faceImageBase64 = faceImage,
                personalNumberHash = base.parsed.hash,
                licenseNumber = licenseNumber,
                address = sub.address.firstText(),
            )
        val sensitive =
            IdCardSensitiveData(
                requestId = requestId,
                name = base.name,
                personalNumber = base.rrnSanitized,
                licenseNumber = licenseNumber,
                licenseSecurityCode = licenseSecurityCode,
                issueDate = base.issueDate,
            )
        return IdCardOcrOutput(result, sensitive)
    }

    private fun mapAlienRegistration(
        requestId: String,
        ac: NcpAlienRegistration,
        confidence: Boolean,
        faceImage: String?,
    ): IdCardOcrOutput {
        val base =
            extractBaseFields(
                rrnRaw = ac.alienRegNum.firstTextOrError("외국인등록번호"),
                nameTexts = ac.name,
                issueDateTexts = ac.issueDate,
                idTypeLabel = "외국인등록증",
            )
        val serialNumber =
            ac.serialNum.firstText()
                ?: throw IdentityException.OcrRejected("외국인등록증 일련번호를 인식하지 못했습니다. 사진을 다시 찍어주세요.")

        val result =
            IdCardRecognitionResult.AlienRegistration(
                name = base.name,
                birthDate = base.parsed.birthDate,
                gender = base.parsed.gender,
                issueDate = base.issueDate,
                rawConfidence = confidence,
                faceImageBase64 = faceImage,
                alienRegNumberHash = base.parsed.hash,
                nationality = ac.nationality.firstText().orEmpty(),
                visaType = ac.visaType.firstText(),
            )
        val sensitive =
            IdCardSensitiveData(
                requestId = requestId,
                name = base.name,
                personalNumber = base.rrnSanitized,
                serialNumber = serialNumber,
                issueDate = base.issueDate,
            )
        return IdCardOcrOutput(result, sensitive)
    }

    private fun mapIdType(raw: String?): IdType =
        when (raw) {
            "ID Card" -> IdType.ID_CARD
            "Driver's License" -> IdType.DRIVER_LICENSE
            "Alien Registration Card" -> IdType.ALIEN_REGISTRATION
            else -> throw IdentityException.OcrRejected("알 수 없는 신분증 타입: $raw")
        }
}

private fun List<NcpText>?.firstText(): String? = this?.firstOrNull()?.text

private fun List<NcpText>?.firstTextOrError(field: String): String = firstText() ?: error("$field 추출 실패")

private data class BaseFields(
    val name: String,
    val rrnSanitized: String,
    val parsed: KoreanRrnParser.Parsed,
    val issueDate: LocalDate,
)

private fun parseDate(text: String): LocalDate? =
    runCatching {
        LocalDate.parse(text.replace(".", "-"))
    }.getOrNull()

private fun extractBaseFields(
    rrnRaw: String,
    nameTexts: List<NcpText>?,
    issueDateTexts: List<NcpText>?,
    idTypeLabel: String,
): BaseFields {
    val rrnSanitized = rrnRaw.replace("-", "").trim()
    val parsed = KoreanRrnParser.parse(rrnSanitized)
    val name = nameTexts.firstTextOrError("이름")
    val issueDate =
        issueDateTexts.firstText()?.let { parseDate(it) }
            ?: throw IdentityException.OcrRejected("$idTypeLabel 발급일을 인식하지 못했습니다. 사진을 다시 찍어주세요.")
    return BaseFields(name, rrnSanitized, parsed, issueDate)
}

private const val EKYC_SECRET_HEADER = "X-EKYC-SECRET"
