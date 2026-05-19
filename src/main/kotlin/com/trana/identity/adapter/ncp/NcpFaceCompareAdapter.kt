package com.trana.identity.adapter.ncp

import com.trana.identity.adapter.FaceCompareAdapter
import com.trana.identity.adapter.FaceCompareResult
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
import org.springframework.web.client.body
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * NCP CLOVA eKYC Face Compare API 어댑터.
 *
 * 신분증 얼굴 사진 + 셀카 → similarity(0.0~1.0) → threshold 적용 isMatch 도출.
 * Secret Key + invoke URL은 `trana.kyc.ncp.face-compare` 사용.
 */
@Component
class NcpFaceCompareAdapter(
    private val props: NcpEkycProperties,
    private val objectMapper: ObjectMapper,
) : FaceCompareAdapter {
    private val client: RestClient =
        RestClient
            .builder()
            .baseUrl(props.faceCompare.invokeUrl)
            .defaultHeader(EKYC_SECRET_HEADER, props.faceCompare.secretKey)
            .build()

    override fun compareFaces(
        idCardImage: ImageInput,
        selfieImage: ImageInput,
    ): FaceCompareResult {
        val body = buildCompareMultipart(idCardImage, selfieImage)
        val response =
            client
                .post()
                .uri("/compare")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body<NcpFaceCompareResponse>()
                ?: error("NCP Face Compare API 응답이 비어 있음")

        check(response.result == "SUCCESS") { "Face Compare 실패: ${response.message}" }

        val similarity =
            response.similarity ?: error("Face Compare 응답에 similarity 없음")

        return FaceCompareResult(
            similarity = similarity,
            isMatch = similarity >= FACE_MATCH_THRESHOLD,
        )
    }

    private fun buildCompareMultipart(
        cardImage: ImageInput,
        faceImage: ImageInput,
    ): MultiValueMap<String, HttpEntity<*>> {
        val body = LinkedMultiValueMap<String, HttpEntity<*>>()

        val messageJson =
            mapOf(
                "requestId" to UUID.randomUUID().toString(),
                "cardImage" to mapOf("format" to cardImage.format.name.lowercase(), "name" to "card"),
                "faceImage" to mapOf("format" to faceImage.format.name.lowercase(), "name" to "face"),
            )
        val messageHeaders = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        body.add("message", HttpEntity(objectMapper.writeValueAsString(messageJson), messageHeaders))

        body.add("cardImage", buildImagePart(cardImage))
        body.add("faceImage", buildImagePart(faceImage))

        return body
    }

    private fun buildImagePart(image: ImageInput): HttpEntity<ByteArrayResource> {
        val resource =
            object : ByteArrayResource(image.bytes) {
                override fun getFilename(): String = image.originalFilename
            }
        val headers = HttpHeaders().apply { contentType = mediaTypeFor(image.format) }
        return HttpEntity(resource, headers)
    }

    private fun mediaTypeFor(format: ImageFormat): MediaType =
        if (format == ImageFormat.PNG) MediaType.IMAGE_PNG else MediaType.IMAGE_JPEG
}

private const val EKYC_SECRET_HEADER = "X-EKYC-SECRET"
private const val FACE_MATCH_THRESHOLD = 0.5
