package com.trana.identity.controller

import com.trana.identity.dto.RecognizeIdCardResponse
import com.trana.identity.dto.RecordPhoneRequest
import com.trana.identity.dto.RecordPhoneResponse
import com.trana.identity.dto.SignUpResponse
import com.trana.identity.dto.VerifyIdCardRequest
import com.trana.identity.dto.VerifyIdCardResponse
import com.trana.identity.service.CompareFacesResult
import com.trana.identity.service.KycSessionService
import com.trana.identity.service.KycSignupService
import com.trana.identity.service.RecognizeIdCardResult
import com.trana.identity.service.RecordPhoneResult
import com.trana.identity.service.VerifyIdCardResult
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/**
 * 성인 본인 KYC Controller.
 *
 * Thin: 비즈니스 로직 X — 변환 + 서비스 위임만.
 */
@RestController
@RequestMapping("/v1/identity")
class IdentityController(
    private val kycSessionService: KycSessionService,
    private val kycSignupService: KycSignupService,
) : IdentityApi {
    override fun recognizeIdCard(
        signupSessionId: UUID,
        file: MultipartFile,
    ): RecognizeIdCardResponse =
        kycSessionService
            .recognizeIdCard(signupSessionId, file.toImageInput())
            .toResponse()

    override fun verifyIdCard(request: VerifyIdCardRequest): VerifyIdCardResponse =
        kycSessionService.verifyIdCard(request.requestId).toResponse()

    override fun recordPhone(request: RecordPhoneRequest): RecordPhoneResponse =
        kycSessionService
            .recordPhone(request.requestId, request.phone)
            .toResponse()

    override fun compareFaces(
        requestId: String,
        file: MultipartFile,
    ): SignUpResponse =
        kycSignupService
            .compareFaces(requestId, file.toImageInput())
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

private fun RecordPhoneResult.toResponse(): RecordPhoneResponse =
    RecordPhoneResponse(requestId = requestId, phone = phone)

private fun CompareFacesResult.toResponse(): SignUpResponse =
    SignUpResponse(
        accessToken = accessToken,
        refreshToken = refreshToken,
        publicCode = publicCode,
        requiresGuardian = requiresGuardian,
    )
