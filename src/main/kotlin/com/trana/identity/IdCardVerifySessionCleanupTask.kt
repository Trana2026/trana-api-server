package com.trana.identity

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 만료된 신분증 Verify 세션 정리 작업.
 *
 * - 매 5분마다 expires_at < now() 인 row 일괄 삭제
 * - 단일 인스턴스 환경 가정 (multi-instance prod 진입 전엔 ShedLock 등 분산 lock 도입 검토)
 */
@Component
class IdCardVerifySessionCleanupTask(
    private val sessionService: IdCardVerifySessionService,
) {
    private val log = LoggerFactory.getLogger(IdCardVerifySessionCleanupTask::class.java)

    @Scheduled(fixedRate = CLEANUP_INTERVAL_MS)
    fun cleanup() {
        val deleted = sessionService.deleteExpired()
        if (deleted > 0) {
            log.info("만료된 KYC 세션 {}건 정리", deleted)
        }
    }
}

private const val CLEANUP_INTERVAL_MS = 300_000L // 5분
