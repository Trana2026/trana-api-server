package com.trana.trustscore.service

import com.trana.dispute.entity.DisputeResolution
import com.trana.dispute.service.DisputeResolvedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 분쟁 운영팀 판정 시 신뢰 점수 자동 적용 listener.
 *
 * @EventListener (synchronous) — DisputeService.resolve 의 tx 안에서 함께 commit.
 * 점수 적용 실패 = 판정도 rollback (안전성 ↑, DB INSERT 2~3건이라 부담 X).
 * cf. TrustScoreSignedListener 와 같은 결.
 *
 * 트리거:
 * - resolution = FRAUD_CONFIRMED → 신고자 +5 + 신고 대상 -15
 * - resolution = FRAUD_DISMISSED → 점수 변동 X (명세 — 알림도 발송 X)
 *
 * FCM 알림은 Phase 4 — 별도 listener (AFTER_COMMIT).
 */
@Component
class TrustScoreDisputeListener(
    private val trustScoreService: TrustScoreService,
) {
    @EventListener
    fun handle(event: DisputeResolvedEvent) {
        if (event.resolution != DisputeResolution.FRAUD_CONFIRMED) return

        trustScoreService.applyFraudReportFiledConfirmed(
            reporterUserId = event.reporterUserId,
            disputeId = event.disputeId,
        )
        trustScoreService.applyFraudReportReceivedConfirmed(
            reportedUserId = event.reportedUserId,
            disputeId = event.disputeId,
        )
    }
}
