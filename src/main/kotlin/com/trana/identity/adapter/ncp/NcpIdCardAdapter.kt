package com.trana.identity.adapter.ncp

import com.trana.identity.IdentityException
import com.trana.identity.adapter.Gender
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
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * NCP CLOVA eKYC Document API 어댑터.
 *
 * 신분증 이미지 → IdCardOcrOutput (외부 result + 내부 sensitive).
 * 4종 지원: 주민등록증(ic) / 운전면허증(dl) / 여권(pp) / 외국인등록증(ac).
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

            IdType.PASSPORT -> {
                mapPassport(requestId, idCard.pp ?: error("pp 정보 없음"), confidence, faceImage)
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
        val rrnRaw = sub.personalNum.firstTextOrError("주민번호")
        val rrnSanitized = rrnRaw.replace("-", "").trim()
        val parsed = KoreanRrnParser.parse(rrnSanitized)
        val name = sub.name.firstTextOrError("이름")
        val issueDate = sub.issueDate.firstText()?.let { parseDate(it) }

        val result =
            IdCardRecognitionResult.ResidentIdCard(
                name = name,
                birthDate = parsed.birthDate,
                gender = parsed.gender,
                issueDate = issueDate,
                rawConfidence = confidence,
                faceImageBase64 = faceImage,
                personalNumberHash = parsed.hash,
                address = sub.address.firstText(),
            )
        val sensitive =
            IdCardSensitiveData(
                requestId = requestId,
                name = name,
                personalNumber = rrnSanitized,
                issueDate = issueDate,
            )
        return IdCardOcrOutput(result, sensitive)
    }

    private fun mapDriverLicense(
        requestId: String,
        sub: NcpIdCardSubject,
        confidence: Boolean,
        faceImage: String?,
    ): IdCardOcrOutput {
        val rrnRaw = sub.personalNum.firstTextOrError("주민번호")
        val rrnSanitized = rrnRaw.replace("-", "").trim()
        val parsed = KoreanRrnParser.parse(rrnSanitized)
        val name = sub.name.firstTextOrError("이름")
        val licenseNumber = sub.num.firstTextOrError("면허번호")
        val licenseSecurityCode = sub.code.firstText()
        val issueDate = sub.issueDate.firstText()?.let { parseDate(it) }

        val result =
            IdCardRecognitionResult.DriverLicense(
                name = name,
                birthDate = parsed.birthDate,
                gender = parsed.gender,
                issueDate = issueDate,
                rawConfidence = confidence,
                faceImageBase64 = faceImage,
                personalNumberHash = parsed.hash,
                licenseNumber = licenseNumber,
                address = sub.address.firstText(),
            )
        val sensitive =
            IdCardSensitiveData(
                requestId = requestId,
                name = name,
                personalNumber = rrnSanitized,
                licenseNumber = licenseNumber,
                licenseSecurityCode = licenseSecurityCode,
                issueDate = issueDate,
            )
        return IdCardOcrOutput(result, sensitive)
    }

    private fun mapPassport(
        requestId: String,
        pp: NcpPassport,
        confidence: Boolean,
        faceImage: String?,
    ): IdCardOcrOutput {
        val fullName = "${pp.surName.firstText().orEmpty()} ${pp.givenName.firstText().orEmpty()}".trim()
        require(fullName.isNotBlank()) { "여권 이름 추출 실패" }

        val birthText = pp.birthDate.firstTextOrError("여권 생년월일")
        val birthDate = parsePassportDate(birthText) ?: error("여권 생년월일 파싱 실패: $birthText")

        val gender =
            when (
                pp.sex
                    .firstTextOrError("여권 성별")
                    .trim()
                    .uppercase()
            ) {
                "M" -> Gender.MALE
                "F" -> Gender.FEMALE
                else -> error("여권 성별 코드 오류")
            }

        val passportNumber = pp.num.firstTextOrError("여권번호")
        val issueDate = pp.issueDate.firstText()?.let { parsePassportDate(it) }
        val expireDate = pp.expireDate.firstText()?.let { parsePassportDate(it) }

        val result =
            IdCardRecognitionResult.Passport(
                name = fullName,
                birthDate = birthDate,
                gender = gender,
                issueDate = issueDate,
                rawConfidence = confidence,
                faceImageBase64 = faceImage,
                passportNumber = passportNumber,
                nationality = pp.nationality.firstText().orEmpty(),
                expireDate = expireDate,
            )
        val sensitive =
            IdCardSensitiveData(
                requestId = requestId,
                name = fullName,
                passportNumber = passportNumber,
                birthDate = birthDate,
                issueDate = issueDate,
                expireDate = expireDate,
            )
        return IdCardOcrOutput(result, sensitive)
    }

    private fun mapAlienRegistration(
        requestId: String,
        ac: NcpAlienRegistration,
        confidence: Boolean,
        faceImage: String?,
    ): IdCardOcrOutput {
        val alienRaw = ac.alienRegNum.firstTextOrError("외국인등록번호")
        val alienSanitized = alienRaw.replace("-", "").trim()
        val parsed = KoreanRrnParser.parse(alienSanitized)
        val name = ac.name.firstTextOrError("이름")
        val serialNumber = ac.serialNum.firstText()
        val issueDate = ac.issueDate.firstText()?.let { parseDate(it) }

        val result =
            IdCardRecognitionResult.AlienRegistration(
                name = name,
                birthDate = parsed.birthDate,
                gender = parsed.gender,
                issueDate = issueDate,
                rawConfidence = confidence,
                faceImageBase64 = faceImage,
                alienRegNumberHash = parsed.hash,
                nationality = ac.nationality.firstText().orEmpty(),
                visaType = ac.visaType.firstText(),
            )
        val sensitive =
            IdCardSensitiveData(
                requestId = requestId,
                name = name,
                personalNumber = alienSanitized,
                serialNumber = serialNumber,
                issueDate = issueDate,
            )
        return IdCardOcrOutput(result, sensitive)
    }

    private fun mapIdType(raw: String?): IdType =
        when (raw) {
            "ID Card" -> IdType.ID_CARD
            "Driver's License" -> IdType.DRIVER_LICENSE
            "Passport" -> IdType.PASSPORT
            "Alien Registration Card" -> IdType.ALIEN_REGISTRATION
            else -> error("알 수 없는 신분증 타입: $raw")
        }

    private fun parseDate(text: String): LocalDate? =
        runCatching {
            LocalDate.parse(text.replace(".", "-"))
        }.getOrNull()

    private fun parsePassportDate(text: String): LocalDate? {
        val cleaned = text.trim()
        return PASSPORT_DATE_PATTERNS.firstNotNullOfOrNull { pattern ->
            runCatching {
                LocalDate.parse(cleaned, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
            }.getOrNull()
        }
    }
}

private fun List<NcpText>?.firstText(): String? = this?.firstOrNull()?.text

private fun List<NcpText>?.firstTextOrError(field: String): String = firstText() ?: error("$field 추출 실패")

private const val EKYC_SECRET_HEADER = "X-EKYC-SECRET"
private val PASSPORT_DATE_PATTERNS =
    listOf("yyyyMMdd", "yyyy.MM.dd", "yyyy-MM-dd", "dd MMM yyyy", "dd-MMM-yyyy")
