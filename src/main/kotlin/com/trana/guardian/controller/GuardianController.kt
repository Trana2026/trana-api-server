package com.trana.guardian.controller

import com.trana.guardian.GuardianProperties
import com.trana.guardian.dto.GuardianLinkResponse
import com.trana.guardian.entity.GuardianLink
import com.trana.guardian.service.GuardianLinkService
import io.swagger.v3.oas.annotations.Parameter
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/guardian")
class GuardianController(
    private val guardianLinkService: GuardianLinkService,
    private val guardianProperties: GuardianProperties,
) : GuardianApi {
    override fun createLink(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
    ): GuardianLinkResponse {
        val link = guardianLinkService.create(userId)
        return link.toResponse(guardianProperties.webBaseUrl)
    }
}

private fun GuardianLink.toResponse(webBaseUrl: String): GuardianLinkResponse =
    GuardianLinkResponse(
        token = token,
        expiresAt = expiresAt,
        verifyUrl = "$webBaseUrl/verify/$token",
    )
