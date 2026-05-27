package com.trana.contract.controller

import com.trana.contract.dto.AiExtractionResponse
import com.trana.contract.dto.ExtractPrefillRequest
import com.trana.contract.service.AiExtractionView
import com.trana.contract.service.ContractAiExtractionService
import io.swagger.v3.oas.annotations.Parameter
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
    ): AiExtractionResponse =
        service
            .extract(
                publicCode = publicCode,
                userId = userId,
                attachmentIds = request.attachmentIds,
                consentedAt = request.consentedAt,
            ).toResponse()

    override fun latest(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
    ): ResponseEntity<AiExtractionResponse> {
        val view = service.getLatest(publicCode, userId) ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(view.toResponse())
    }
}

private fun AiExtractionView.toResponse(): AiExtractionResponse =
    AiExtractionResponse(
        extractionId = extractionId,
        model = model,
        promptVersion = promptVersion,
        prefill = prefill,
        latencyMs = latencyMs,
        usage = usage,
        extractedAt = extractedAt,
    )
