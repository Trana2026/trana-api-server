package com.trana.terms

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/consents")
@SecurityRequirement(name = "bearerAuth")
class ConsentController(
    private val userConsentService: UserConsentService,
) : ConsentApi {
    override fun agree(
        request: AgreeRequest,
        @AuthenticationPrincipal userId: Long,
        httpRequest: HttpServletRequest,
    ): List<ConsentResponse> {
        val command =
            AgreeCommand(
                userId = userId,
                termsVersionIds = request.termsVersionIds,
                contextType = request.contextType,
                ageGroup = request.ageGroup,
                ip = extractIp(httpRequest),
                userAgent = httpRequest.getHeader("User-Agent"),
                signupSessionId = request.signupSessionId,
                contextId = request.contextId,
            )
        return userConsentService.agree(command).map { it.toResponse() }
    }

    private fun extractIp(request: HttpServletRequest): String {
        val xff = request.getHeader("X-Forwarded-For")
        return if (xff.isNullOrBlank()) request.remoteAddr else xff.split(",").first().trim()
    }
}

private fun UserConsent.toResponse() =
    ConsentResponse(
        id = id!!,
        termsVersionId = termsVersionId,
        agreedAt = agreedAt,
    )
