package com.trana.identity.controller

import com.trana.identity.dto.GuardianBindResponse
import com.trana.identity.dto.GuardianVerifyIdCardRequest
import com.trana.identity.dto.RecognizeIdCardResponse
import com.trana.identity.dto.VerifyIdCardResponse
import com.trana.identity.service.CompareGuardianResult
import com.trana.identity.service.KycGuardianService
import com.trana.identity.service.RecognizeIdCardResult
import com.trana.identity.service.VerifyIdCardResult
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/v1/identity/guardian")
class GuardianIdentityController(
    private val kycGuardianService: KycGuardianService,
) : GuardianIdentityApi {
    override fun recognizeIdCard(
        token: String,
        file: MultipartFile,
    ): RecognizeIdCardResponse =
        kycGuardianService
            .recognizeIdCard(token, file.toImageInput())
            .toResponse()

    override fun verifyIdCard(request: GuardianVerifyIdCardRequest): VerifyIdCardResponse =
        kycGuardianService.verifyIdCard(request.requestId, request.token).toResponse()

    override fun compareFaces(
        requestId: String,
        token: String,
        file: MultipartFile,
    ): GuardianBindResponse =
        kycGuardianService
            .compareFaces(requestId, token, file.toImageInput())
            .toResponse()
}

private fun RecognizeIdCardResult.toResponse(): RecognizeIdCardResponse =
    RecognizeIdCardResponse(
        requestId = requestId,
        idType = idType,
        name = name,
        birthDate = birthDate,
        gender = gender.name,
    )

private fun VerifyIdCardResult.toResponse(): VerifyIdCardResponse =
    VerifyIdCardResponse(requestId = requestId, verified = verified)

private fun CompareGuardianResult.toResponse(): GuardianBindResponse =
    GuardianBindResponse(subjectUserId = subjectUserId, guardianId = guardianId, verified = verified)
