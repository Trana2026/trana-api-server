package com.trana.identity.service

import com.trana.identity.repository.IdCardVerifySessionRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 만료된 id_card_verify_sessions 일괄 삭제.
 *
 * - 1분마다 실행 (TTL 10분 대비 충분한 빈도)
 * - 첫 실행은 앱 부팅 1분 후 (initialDelay)
 * - 멀티 인스턴스 환경에서도 안전 — DELETE는 idempotent
 */
@Component
class IdCardVerifySessionCleanupTask(
    private val repository: IdCardVerifySessionRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    @Transactional
    fun cleanupExpired() {
        val deleted = repository.deleteExpired(Instant.now())
        if (deleted > 0) {
            log.info("Cleaned up {} expired id_card_verify_sessions", deleted)
        }
    }
}
