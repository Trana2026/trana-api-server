package com.trana.dispute.controller

import com.trana.common.dev.DevProperties
import com.trana.dispute.service.DisputeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * 개발 전용 — 분쟁(신고) 운영팀 사기 판정 endpoint.
 *
 * 보안:
 * - @Profile("local", "dev") — prod 빈 자체 X (404)
 * - X-Dev-Token-Key 헤더 필수 (DevTokenController 패턴 동일)
 *
 * W7 RBAC 도입 + 운영자 admin endpoint 신설 후 이 controller 제거.
 */
@Profile("local", "dev")
@RestController
@RequestMapping("/v1/dev/disputes")
@Tag(name = "Dev", description = "개발 전용 (local/dev profile)")
class DevDisputeController(
    private val disputeService: DisputeService,
    private val devProperties: DevProperties,
) {
    @Operation(
        summary = "분쟁 사기 판정 (dev only)",
        description = """
    운영팀 판정 시뮬레이션 — 신뢰 점수 트리거 (TrustScoreDisputeListener) 검증 용.

    - fraud=true → DisputeRecord.markFraudConfirmed → 신고자 +5 / 신고 대상 -15
    - fraud=false → markFraudDismissed → 점수 변동 X

    prod 비활성. W7 RBAC + admin endpoint 도입 시 제거.
    """,
    )
    @PostMapping("/{disputeId}/resolve")
    fun resolve(
        @RequestHeader(value = "X-Dev-Token-Key", required = false) providedKey: String?,
        @PathVariable disputeId: Long,
        @RequestBody request: DevResolveDisputeRequest,
    ) {
        if (providedKey != devProperties.tokenKey) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "X-Dev-Token-Key 헤더 검증 실패")
        }
        disputeService.resolve(
            disputeId = disputeId,
            fraud = request.fraud,
            adminUserId = request.adminUserId,
            reason = request.reason,
        )
    }
}

data class DevResolveDisputeRequest(
    /** true = FRAUD_CONFIRMED (-15/+5 트리거) / false = FRAUD_DISMISSED (점수 변동 X) */
    val fraud: Boolean,
    /** 판정 운영자 ID (W7 RBAC 도입 전까지 임의 user ID) */
    val adminUserId: Long,
    /** 판정 사유 (audit 영구 보존) */
    val reason: String,
)
