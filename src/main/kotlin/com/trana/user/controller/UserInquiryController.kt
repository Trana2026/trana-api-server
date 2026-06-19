package com.trana.user.controller

import com.trana.user.dto.CreateInquiryRequest
import com.trana.user.dto.InquiryDetailResponse
import com.trana.user.dto.InquirySummaryResponse
import com.trana.user.entity.UserInquiry
import com.trana.user.service.UserInquiryService
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/users/me")
@SecurityRequirement(name = "bearerAuth")
class UserInquiryController(
    private val userInquiryService: UserInquiryService,
) : UserInquiryApi {
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
}

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
