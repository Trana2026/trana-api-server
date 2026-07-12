package com.trana.dispute.service

import com.trana.dispute.repository.DisputeRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * 분쟁 이력 3년 후 cleanup (전자문서법 보존기간).
 *
 * 정책:
 * - resolved 3년 경과 + resolution=FRAUD_DISMISSED (또는 PENDING 이후 다른 값) → 삭제
 * - FRAUD_CONFIRMED → 영구 보존 (사기 이력, trust_score_events 정책과 짝)
 * - PENDING (미해결) → 삭제 X (resolvedAt IS NULL 조건)
 *
 * 매일 자정 KST 실행. ShedLock 미도입 (cross-cutting #183 통합 대상).
 */
@Component
@Transactional
class DisputeCleanupTask(
    private val disputeRecordRepository: DisputeRecordRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    fun cleanupAfterRetention() {
        val threshold = Instant.now().minus(RETENTION_DURATION)
        val deleted = disputeRecordRepository.deleteResolvedBeforeAndResolutionNotConfirmed(threshold)
        log.info("[DISPUTE_CLEANUP] deleted={} threshold={}", deleted, threshold)
    }

    companion object {
        private val RETENTION_DURATION: Duration = Duration.ofDays(365L * 3)
    }
}
