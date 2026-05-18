package com.trana.guardian

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/guardian")
class GuardianController(
    private val guardianLinkService: GuardianLinkService,
) : GuardianApi {
    override fun createLink(
        @AuthenticationPrincipal userId: Long,
    ): GuardianLinkCreateResponse {
        val link = guardianLinkService.createLink(userId)
        return GuardianLinkCreateResponse(
            token = link.token,
            expiresAt = link.expiresAt,
        )
    }

    override fun getLink(token: String): GuardianLinkInfoResponse {
        val link = guardianLinkService.findValidLink(token)
        return GuardianLinkInfoResponse(expiresAt = link.expiresAt)
    }
}
