package com.trana.contract.controller

import com.trana.contract.ContractException
import com.trana.contract.dto.ConfirmMinorDisclosureRequest
import com.trana.contract.dto.MinorDisclosureConfirmationResponse
import com.trana.contract.dto.MinorDisclosureTemplateResponse
import com.trana.contract.repository.ContractRepository
import com.trana.contract.service.MinorDisclosureConfirmationService
import com.trana.contract.service.MinorDisclosureTemplate
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/contracts")
@SecurityRequirement(name = "bearerAuth")
class MinorDisclosureController(
    private val service: MinorDisclosureConfirmationService,
    private val contractRepository: ContractRepository,
) : MinorDisclosureApi {
    override fun confirm(
        @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
        request: ConfirmMinorDisclosureRequest,
        httpRequest: HttpServletRequest,
    ): MinorDisclosureConfirmationResponse {
        val contract =
            contractRepository.findByPublicCodeAndDeletedAtIsNull(publicCode)
                ?: throw ContractException.NotFound(publicCode)
        val saved =
            service.confirm(
                contract = contract,
                partyUserId = userId,
                disclosedAt = request.disclosedAt,
                ip = extractIp(httpRequest),
                userAgent = httpRequest.getHeader("User-Agent"),
            )
        return MinorDisclosureConfirmationResponse(
            confirmedAt = requireNotNull(saved.confirmedAt) { "confirmedAt 은 @CreationTimestamp 로 채워져야 함" },
            templateVersion = saved.templateVersion,
        )
    }

    private fun extractIp(request: HttpServletRequest): String {
        val xff = request.getHeader("X-Forwarded-For")
        return if (xff.isNullOrBlank()) request.remoteAddr else xff.split(",").first().trim()
    }

    override fun latest(): MinorDisclosureTemplateResponse =
        MinorDisclosureTemplateResponse(
            version = MinorDisclosureTemplate.LATEST_VERSION,
            title = MinorDisclosureTemplate.TITLE,
            items = MinorDisclosureTemplate.ITEMS,
        )
}
