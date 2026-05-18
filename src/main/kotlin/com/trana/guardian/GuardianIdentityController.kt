package com.trana.guardian

import com.trana.identity.FaceCompareResponse
import com.trana.identity.IdCardOcrResponse
import com.trana.identity.IdCardVerifyResponse
import com.trana.identity.adapter.ImageFormat
import com.trana.identity.adapter.ImageInput
import com.trana.identity.toResponse
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/v1/guardian/identity")
class GuardianIdentityController(
    private val guardianIdentityService: GuardianIdentityService,
) : GuardianIdentityApi {
    override fun recognizeIdCard(
        file: MultipartFile,
        token: String,
    ): IdCardOcrResponse =
        guardianIdentityService
            .recognizeIdCard(file.toImageInput(), token)
            .toResponse()

    override fun verifyIdCard(request: GuardianIdCardVerifyRequest): IdCardVerifyResponse =
        guardianIdentityService
            .verifyIdCard(request.requestId, request.token)
            .toResponse()

    override fun compareFaces(
        faceImage: MultipartFile,
        requestId: String,
        token: String,
    ): FaceCompareResponse =
        guardianIdentityService
            .compareFaces(requestId, faceImage.toImageInput(), token)
            .toResponse()
}

private fun MultipartFile.toImageInput(): ImageInput {
    val format = detectImageFormat(contentType, originalFilename)
    return ImageInput(
        bytes = bytes,
        format = format,
        originalFilename = originalFilename ?: "image.${format.name.lowercase()}",
    )
}

private fun detectImageFormat(
    contentType: String?,
    filename: String?,
): ImageFormat =
    when (contentType?.lowercase()) {
        "image/png" -> {
            ImageFormat.PNG
        }

        "image/jpeg", "image/jpg" -> {
            ImageFormat.JPG
        }

        else -> {
            when (filename?.substringAfterLast('.', "")?.lowercase()) {
                "png" -> ImageFormat.PNG
                "jpg", "jpeg" -> ImageFormat.JPG
                else -> error("지원하지 않는 이미지 포맷: contentType=$contentType, filename=$filename")
            }
        }
    }
