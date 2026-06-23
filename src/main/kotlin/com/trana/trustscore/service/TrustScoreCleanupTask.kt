package com.trana.trustscore.service

import com.trana.trustscore.repository.TrustScoreEventRepository
import com.trana.user.entity.UserStatus
import com.trana.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 탈퇴 1년 경과 + 사기 X user 의 trust_score_events 일괄 삭제 cron.
 *
 * 명세 부록:
 * - 일반 user 탈퇴 → 점수 + 변동 이력 1년 보존 후 자동 삭제
 * - 사기 확인 이력 (fraudReportReceivedCount > 0) → 영구 보존 (cleanup 대상 X)
 *
 * - 매일 00:30 KST 실행 (TicketExpiryTask 00:00 KST 와 30분 차)
 * - DELETE 는 V10 trigger 가 trust_score_events 만 허용 (UPDATE 는 차단, audit immutability)
 *
 * 멀티 인스턴스 락 보류 (W10+) — TicketIssueTask / ExpiryTask 와 동일.
 */
@Component
class TrustScoreCleanupTask(
    private val userRepository: UserRepository,
    private val eventRepository: TrustScoreEventRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Seoul")
    @Transactional
    fun cleanupAfterRetention() {
        val threshold = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS)
        val targets =
            userRepository.findAllByStatusAndWithdrawnAtBeforeAndFraudReportReceivedCount(
                UserStatus.WITHDRAWN,
                threshold,
                FRAUD_COUNT_NONE,
            )
        if (targets.isEmpty()) {
            log.debug("[TrustScoreCleanupTask] no retention-expired users")
            return
        }
        val userIds = targets.mapNotNull { it.id }
        eventRepository.deleteAllByUserIdIn(userIds)
        log.info(
            "[TrustScoreCleanupTask] deleted events for {} withdrawn users (threshold={})",
            userIds.size,
            threshold,
        )
    }

    companion object {
        private const val RETENTION_DAYS = 365L
        private const val FRAUD_COUNT_NONE = 0
    }
}
