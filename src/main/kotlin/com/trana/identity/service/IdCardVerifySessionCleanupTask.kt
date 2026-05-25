package com.trana.identity.service

import com.trana.identity.repository.IdCardVerifySessionRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 만료된 id_card_verify_sessions 일괄 정리 (S3 객체 + PENDING verification 포함).
 *
 * - 1분마다 실행 (TTL 10분 대비 충분한 빈도)
 * - 각 row마다 IdentitySessionPurger.purgeByRequestId 위임
 * - SUCCESS/FAILED verification은 audit 가치라 보존 (PENDING만 정리)
 * - 첫 실행은 앱 부팅 1분 후 (initialDelay)
 * - 멀티 인스턴스 환경에서도 안전 — purger의 모든 delete는 idempotent
 */
@Component
class IdCardVerifySessionCleanupTask(
    private val sessionRepository: IdCardVerifySessionRepository,
    private val purger: IdentitySessionPurger,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    @Transactional
    fun cleanupExpired() {
        val expired = sessionRepository.findAllByExpiresAtBefore(Instant.now())
        if (expired.isEmpty()) return

        expired.forEach { purger.purgeByRequestId(it.requestId) }
        log.info("Cleaned up {} expired id_card_verify_sessions", expired.size)
    }
}
