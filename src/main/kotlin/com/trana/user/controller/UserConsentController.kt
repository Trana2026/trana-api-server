package com.trana.user.controller

import com.trana.terms.dto.MyConsentResponse
import com.trana.terms.service.ConsentService
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/users/me")
@SecurityRequirement(name = "bearerAuth")
class UserConsentController(
    private val consentService: ConsentService,
) : UserConsentApi {
    override fun getMyConsents(
        @AuthenticationPrincipal userId: Long,
    ): List<MyConsentResponse> = consentService.findMyConsents(userId)
}
