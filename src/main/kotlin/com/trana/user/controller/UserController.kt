package com.trana.user.controller

import com.trana.terms.dto.MyConsentResponse
import com.trana.terms.service.ConsentService
import com.trana.user.dto.CreateInquiryRequest
import com.trana.user.dto.InquiryDetailResponse
import com.trana.user.dto.InquirySummaryResponse
import com.trana.user.dto.MeResponse
import com.trana.user.dto.PushEnabledResponse
import com.trana.user.dto.UpdatePushEnabledRequest
import com.trana.user.entity.User
import com.trana.user.entity.UserInquiry
import com.trana.user.service.UserInquiryService
import com.trana.user.service.UserService
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/users")
@SecurityRequirement(name = "bearerAuth")
class UserController(
    private val userService: UserService,
    private val consentService: ConsentService,
    private val userInquiryService: UserInquiryService,
) : UserApi {
    override fun getMe(
        @AuthenticationPrincipal userId: Long,
    ): MeResponse = userService.getById(userId).toMeResponse()

    override fun withdraw(
        @AuthenticationPrincipal userId: Long,
    ) {
        userService.withdraw(userId)
    }

    override fun getMyConsents(
        @AuthenticationPrincipal userId: Long,
    ): List<MyConsentResponse> = consentService.findMyConsents(userId)

    override fun createInquiry(
        @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: CreateInquiryRequest,
    ): InquirySummaryResponse =
        userInquiryService
            .create(
                userId = userId,
                email = request.email,
                title = request.title,
                content = request.content,
            ).toSummary()

    override fun listMyInquiries(
        @AuthenticationPrincipal userId: Long,
    ): List<InquirySummaryResponse> = userInquiryService.findMine(userId).map { it.toSummary() }

    override fun getMyInquiry(
        @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
    ): InquiryDetailResponse = userInquiryService.findByPublicCode(publicCode, userId).toDetail()

    override fun changePushEnabled(
        @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: UpdatePushEnabledRequest,
    ): PushEnabledResponse {
        val user = userService.changePushEnabled(userId, request.enabled)
        return PushEnabledResponse(pushEnabled = user.pushEnabled)
    }
}

private fun User.toMeResponse(): MeResponse =
    MeResponse(
        publicCode = publicCode,
        email = email,
        status = status,
        ageGroup = ageGroup,
        guardianVerifiedAt = guardianVerifiedAt,
        name = name,
        birthDate = birthDate,
        gender = gender,
        phone = phone,
        pushEnabled = pushEnabled,
    )

private fun UserInquiry.toSummary(): InquirySummaryResponse =
    InquirySummaryResponse(
        publicCode = publicCode,
        title = title,
        createdAt = createdAt!!,
    )

private fun UserInquiry.toDetail(): InquiryDetailResponse =
    InquiryDetailResponse(
        publicCode = publicCode,
        email = email,
        title = title,
        content = content,
        createdAt = createdAt!!,
    )
