package com.trana.contract.controller

import com.trana.contract.dto.CancellationRequestRequest
import com.trana.contract.dto.CancellationRequestResponse
import com.trana.contract.service.ContractCancellationService
import io.swagger.v3.oas.annotations.Parameter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/contracts")
class ContractCancellationController(
    private val service: ContractCancellationService,
) : ContractCancellationApi {
    override fun request(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
        @RequestBody request: CancellationRequestRequest,
        httpRequest: HttpServletRequest,
    ): CancellationRequestResponse {
        val record =
            service.request(
                publicCode = publicCode,
                requesterUserId = userId,
                reason = request.reason,
                detail = request.detail,
                requesterIp = httpRequest.remoteAddr,
            )
        return CancellationRequestResponse.from(record, viewerUserId = userId)
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    override fun confirm(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
    ) {
        service.confirm(publicCode = publicCode, userId = userId)
    }

    override fun getActive(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
    ): CancellationRequestResponse? {
        val record = service.findActive(publicCode = publicCode, userId = userId) ?: return null
        return CancellationRequestResponse.from(record, viewerUserId = userId)
    }
}
