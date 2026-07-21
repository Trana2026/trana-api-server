package com.trana.contract.controller

import com.trana.contract.dto.AiExtractionResponse
import com.trana.contract.dto.ExtractPrefillRequest
import com.trana.contract.service.AiExtractionStatusView
import com.trana.contract.service.ContractAiExtractionService
import io.swagger.v3.oas.annotations.Parameter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/contracts/{publicCode}/ai-extractions")
class ContractAiController(
    private val service: ContractAiExtractionService,
) : ContractAiApi {
    override fun extract(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
        @RequestBody request: ExtractPrefillRequest,
        @Parameter(hidden = true) httpRequest: HttpServletRequest,
    ): ResponseEntity<AiExtractionResponse> {
        val view =
            service.submit(
                publicCode = publicCode,
                userId = userId,
                attachmentIds = request.attachmentIds,
                consentedAt = request.consentedAt,
                consenterIp = extractIp(httpRequest),
            )
        return ResponseEntity.accepted().body(view.toResponse())
    }

    private fun extractIp(request: HttpServletRequest): String {
        val xff = request.getHeader("X-Forwarded-For")
        return if (xff.isNullOrBlank()) request.remoteAddr else xff.split(",").first().trim()
    }

    override fun latest(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
    ): ResponseEntity<AiExtractionResponse> {
        val view = service.getLatest(publicCode, userId) ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(view.toResponse())
    }

    override fun getById(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
        @PathVariable extractionId: Long,
    ): ResponseEntity<AiExtractionResponse> {
        val view = service.getById(publicCode, extractionId, userId)
        return ResponseEntity.ok(view.toResponse())
    }
}

private fun AiExtractionStatusView.toResponse(): AiExtractionResponse =
    AiExtractionResponse(
        extractionId = extractionId,
        status = status,
        model = model,
        promptVersion = promptVersion,
        prefill = prefill,
        latencyMs = latencyMs,
        usage = usage,
        errorMessage = errorMessage,
        extractedAt = extractedAt,
    )
