package com.trana.dispute.service

import com.trana.dispute.entity.DisputeResolution
import java.time.Instant

/**
 * 분쟁(신고) 운영팀 판정 완료 이벤트.
 *
 * DisputeService 가 markFraudConfirmed / markFraudDismissed 호출 후 publish.
 *
 * Listener:
 * - TrustScoreDisputeListener — resolution == FRAUD_CONFIRMED 만 처리. 신고자 +5 / 신고 대상 -15
 * - (W7+) DisputeAuditListener — audit_logs INSERT (필요 시 신규 추가)
 *
 * payload — listener 의 추가 도메인 조회 부담 줄이려고 신고자/신고 대상 양쪽 모두 미리 계산해서 전달:
 * - reporterUserId : dispute_records.reporter_user_id
 * - reportedUserId : contract 의 reporter 가 아닌 상대편 (publisher 가 contract_parties 조회 후 계산)
 */
data class DisputeResolvedEvent(
    val disputeId: Long,
    val contractId: Long,
    val reporterUserId: Long,
    val reportedUserId: Long,
    val resolution: DisputeResolution,
    val resolvedByAdminUserId: Long,
    val resolvedAt: Instant,
)
