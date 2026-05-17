package com.trana.identity

import com.trana.identity.adapter.ImageFormat
import com.trana.identity.adapter.ImageInput
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/v1/identity")
class IdentityController(
    private val identityService: IdentityService,
) : IdentityApi {
    override fun recognizeIdCard(file: MultipartFile): IdCardOcrResponse =
        identityService.recognizeIdCard(file.toImageInput()).toResponse()

    override fun verifyIdCard(request: IdCardVerifyRequest): IdCardVerifyResponse =
        identityService.verifyIdCard(request.requestId).toResponse()

    override fun compareFaces(
        cardImage: MultipartFile,
        faceImage: MultipartFile,
    ): FaceCompareResponse =
        identityService
            .compareFaces(cardImage.toImageInput(), faceImage.toImageInput())
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
