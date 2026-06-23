package com.trana.trustscore.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 면제 티켓 만료 처리 + 만료 임박 알림 cron.
 *
 * - 매일 00:00 KST 실행 (issue task 와 동시 발화 — issue 가 매월 1일에만, expire 는 매일)
 * - expireBatch : UNUSED + expires_at < now() → status=EXPIRED
 * - notifyExpiringSoonBatch : UNUSED + expires_at < now+3d + expiry_notified_at IS NULL → FCM + 마킹
 *
 * 멀티 인스턴스 락 보류 (W10+) — issue task 와 동일.
 */
@Component
class TicketExpiryTask(
    private val service: WarrantyExemptionTicketService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    fun expireDaily() {
        val expiredCount = service.expireBatch()
        val notifiedCount = service.notifyExpiringSoonBatch()
        log.info(
            "[TicketExpiryTask] daily fired — expired={}, notified={}",
            expiredCount,
            notifiedCount,
        )
    }
}
