package com.trana.guardian.service

import com.trana.guardian.repository.GuardianLinkRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 만료된 guardian_links 일괄 삭제.
 *
 * - 1시간마다 실행 (TTL 3일 대비 충분한 빈도)
 * - 사용 여부 무관 — audit는 identity_verifications에 영구 보존
 */
@Component
class GuardianLinkCleanupTask(
    private val repository: GuardianLinkRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    @Transactional
    fun cleanupExpired() {
        val deleted = repository.deleteExpired(Instant.now())
        if (deleted > 0) {
            log.info("Cleaned up {} expired guardian_links", deleted)
        }
    }
}
