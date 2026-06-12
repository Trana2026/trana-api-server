package com.trana.dispute.controller

import com.trana.dispute.dto.DisputeListResponse
import com.trana.dispute.dto.DisputeReportRequest
import com.trana.dispute.dto.DisputeResponse
import com.trana.dispute.service.DisputeService
import com.trana.dispute.service.EvidencePackageService
import io.swagger.v3.oas.annotations.Parameter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.io.ByteArrayOutputStream

@RestController
@RequestMapping("/v1/contracts")
class DisputeController(
    private val service: DisputeService,
    private val evidencePackageService: EvidencePackageService,
) : DisputeApi {
    override fun report(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
        @RequestBody request: DisputeReportRequest,
        httpRequest: HttpServletRequest,
    ): DisputeResponse {
        val record =
            service.report(
                publicCode = publicCode,
                reporterUserId = userId,
                reason = request.reason,
                detail = request.detail,
                reporterIp = httpRequest.remoteAddr,
            )
        return DisputeResponse.from(record, viewerUserId = userId)
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    override fun cancelByReporter(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
        @PathVariable disputeId: Long,
    ) {
        service.cancelByReporter(publicCode = publicCode, disputeId = disputeId, userId = userId)
    }

    override fun list(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
    ): DisputeListResponse {
        val records = service.list(publicCode = publicCode, userId = userId)
        return DisputeListResponse(
            disputes = records.map { DisputeResponse.from(it, viewerUserId = userId) },
        )
    }

    override fun evidencePackage(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
    ): ResponseEntity<Resource> {
        val payload = evidencePackageService.authorizeAndCollect(publicCode = publicCode, userId = userId)

        val buffer = ByteArrayOutputStream()
        evidencePackageService.writeZip(payload, buffer)
        val bytes = buffer.toByteArray()
        val resource = ByteArrayResource(bytes)

        val filename = "evidence-${payload.publicCode}.zip"
        return ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_TYPE, "application/zip")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .contentLength(bytes.size.toLong())
            .body(resource)
    }
}
