package com.trana.identity

import com.trana.identity.adapter.FaceCompareAdapter
import com.trana.identity.adapter.IdCardOcrAdapter
import com.trana.identity.adapter.IdCardSensitiveData
import com.trana.identity.adapter.IdCardVerifyAdapter
import com.trana.identity.adapter.IdCardVerifyInput
import com.trana.identity.adapter.IdType
import com.trana.identity.adapter.ImageFormat
import com.trana.identity.adapter.ImageInput
import com.trana.identity.adapter.idType
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.OffsetDateTime

@RestController
@RequestMapping("/v1/identity")
class IdentityController(
    private val idCardOcrAdapter: IdCardOcrAdapter,
    private val idCardVerifyAdapter: IdCardVerifyAdapter,
    private val faceCompareAdapter: FaceCompareAdapter,
    private val sessionService: IdCardVerifySessionService,
) : IdentityApi {
    override fun recognizeIdCard(file: MultipartFile): IdCardOcrResponse {
        val output = idCardOcrAdapter.recognizeIdCard(file.toImageInput())
        sessionService.save(output.sensitive.toSessionData(output.result.idType))
        return output.toResponse()
    }

    override fun verifyIdCard(request: IdCardVerifyRequest): IdCardVerifyResponse {
        val session = sessionService.findActive(request.requestId)
        return idCardVerifyAdapter.verify(session.toVerifyInput()).toResponse()
    }

    override fun compareFaces(
        cardImage: MultipartFile,
        faceImage: MultipartFile,
    ): FaceCompareResponse =
        faceCompareAdapter
            .compareFaces(
                idCardImage = cardImage.toImageInput(),
                selfieImage = faceImage.toImageInput(),
            ).toResponse()
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

private fun IdCardSensitiveData.toSessionData(idType: IdType): IdCardSessionData =
    IdCardSessionData(
        requestId = requestId,
        idType = idType,
        name = name,
        personalNumber = personalNumber,
        licenseNumber = licenseNumber,
        licenseSecurityCode = licenseSecurityCode,
        passportNumber = passportNumber,
        birthDate = birthDate,
        serialNumber = serialNumber,
        issueDate = issueDate,
        expireDate = expireDate,
        expiresAt = OffsetDateTime.now().plusMinutes(SESSION_TTL_MINUTES),
    )

private fun IdCardSessionData.toVerifyInput(): IdCardVerifyInput {
    val base =
        IdCardVerifyInput(
            requestId = requestId,
            idType = idType,
            name = name,
            issueDate = issueDate,
        )
    return when (idType) {
        IdType.ID_CARD -> {
            base.copy(personalNum = personalNumber)
        }

        IdType.DRIVER_LICENSE -> {
            base.copy(
                personalNum = personalNumber,
                licenseNum = licenseNumber,
                licenseCode = licenseSecurityCode,
            )
        }

        IdType.PASSPORT -> {
            base.copy(
                passportNum = passportNumber,
                birthDate = birthDate,
                expireDate = expireDate,
            )
        }

        IdType.ALIEN_REGISTRATION -> {
            base.copy(
                alienRegNum = personalNumber,
                serialNum = serialNumber,
            )
        }
    }
}

private const val SESSION_TTL_MINUTES = 10L
