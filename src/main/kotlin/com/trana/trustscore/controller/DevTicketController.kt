package com.trana.trustscore.controller

import com.trana.common.dev.DevProperties
import com.trana.trustscore.service.IssueBatchResult
import com.trana.trustscore.service.WarrantyExemptionTicketService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * 개발 전용 — 면제 티켓 batch 수동 trigger (cron 안 기다리고 즉시 발사).
 *
 * - issue : 매월 1일 발급 즉시 실행
 * - expire : 만료 처리 즉시 실행
 * - notify-expiring-soon : 만료 3일 전 알림 즉시 실행
 *
 * 보안: @Profile("local", "dev") + X-Dev-Token-Key 헤더 (DevTokenController 패턴 동일).
 */
@Profile("local", "dev")
@RestController
@RequestMapping("/v1/dev/tickets")
@Tag(name = "Dev", description = "개발 전용 (local/dev profile)")
class DevTicketController(
    private val ticketService: WarrantyExemptionTicketService,
    private val devProperties: DevProperties,
) {
    @Operation(summary = "면제 티켓 매월 발급 batch 즉시 발사 (dev only)")
    @PostMapping("/issue-monthly")
    fun issueMonthly(
        @RequestHeader(value = "X-Dev-Token-Key", required = false) providedKey: String?,
    ): IssueBatchResult {
        verifyKey(providedKey)
        return ticketService.issueMonthlyBatch()
    }

    @Operation(summary = "만료 batch 즉시 발사 (dev only)")
    @PostMapping("/expire")
    fun expire(
        @RequestHeader(value = "X-Dev-Token-Key", required = false) providedKey: String?,
    ): Int {
        verifyKey(providedKey)
        return ticketService.expireBatch()
    }

    @Operation(summary = "만료 임박 알림 batch 즉시 발사 (dev only)")
    @PostMapping("/notify-expiring-soon")
    fun notifyExpiringSoon(
        @RequestHeader(value = "X-Dev-Token-Key", required = false) providedKey: String?,
    ): Int {
        verifyKey(providedKey)
        return ticketService.notifyExpiringSoonBatch()
    }

    private fun verifyKey(providedKey: String?) {
        if (providedKey != devProperties.tokenKey) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "X-Dev-Token-Key 헤더 검증 실패")
        }
    }

    @Operation(
        summary = "면제 티켓 1장 사용 (dev only)",
        description =
            "결제 시스템 도입 전 임시 — 보유 UNUSED 티켓 1장을 contractId 에 매핑해 USED 처리. " +
                "보유 티켓 없으면 409 TRUST_SCORE_409_NO_TICKET. " +
                "W10+ 결제 endpoint 도입 시 이 endpoint 제거.",
    )
    @PostMapping("/use")
    fun use(
        @RequestHeader(value = "X-Dev-Token-Key", required = false) providedKey: String?,
        @RequestBody request: DevUseTicketRequest,
    ): DevUseTicketResponse {
        verifyKey(providedKey)
        val ticket = ticketService.useTicket(userId = request.userId, contractId = request.contractId)
        return DevUseTicketResponse(
            ticketId = requireNotNull(ticket.id),
            usedContractId = ticket.usedContractId,
        )
    }
}

data class DevUseTicketRequest(
    val userId: Long,
    val contractId: Long,
)

data class DevUseTicketResponse(
    val ticketId: Long,
    val usedContractId: Long?,
)
