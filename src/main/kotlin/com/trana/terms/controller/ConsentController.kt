package com.trana.terms.controller

import com.trana.terms.dto.AgreeRequest
import com.trana.terms.dto.ConsentBatchResponse
import com.trana.terms.dto.ConsentResponse
import com.trana.terms.entity.UserConsent
import com.trana.terms.service.AgreeCommand
import com.trana.terms.service.ConsentService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/consents")
class ConsentController(
    private val consentService: ConsentService,
) : ConsentApi {
    override fun agree(
        request: AgreeRequest,
        @AuthenticationPrincipal userId: Long?,
        httpRequest: HttpServletRequest,
    ): ConsentBatchResponse {
        val command =
            AgreeCommand(
                termsVersionIds = request.termsVersionIds,
                contextType = request.contextType,
                ageGroup = request.ageGroup,
                ip = extractIp(httpRequest),
                userId = userId,
                contextId = request.contextId,
                signupSessionId = request.signupSessionId,
                userAgent = httpRequest.getHeader("User-Agent"),
            )
        val consents = consentService.agree(command)
        return ConsentBatchResponse(
            signupSessionId = consents.firstOrNull()?.signupSessionId,
            consents = consents.map { it.toResponse() },
        )
    }

    private fun extractIp(request: HttpServletRequest): String {
        val xff = request.getHeader("X-Forwarded-For")
        return if (xff.isNullOrBlank()) request.remoteAddr else xff.split(",").first().trim()
    }
}

private fun UserConsent.toResponse() =
    ConsentResponse(
        id = checkNotNull(id) { "UserConsent id should be assigned" },
        termsVersionId = termsVersionId,
        agreedAt = checkNotNull(agreedAt) { "UserConsent agreedAt should be assigned" },
    )
