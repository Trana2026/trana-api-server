package com.trana.trustscore.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 면제 티켓 매월 1일 자동 발급 cron.
 *
 * - 매월 1일 00:00 KST 실행
 * - 신뢰 등급 (55~74) user → 1장 / 우수 등급 (75~89) user → 3장
 * - 멱등 — 같은 달 + 같은 사유 중복 발급 차단 (WarrantyExemptionTicketService 내부 처리)
 *
 * 운영 보류 (W10+):
 * - ShedLock — 멀티 인스턴스 N≥2 진입 시 중복 실행 차단 필요
 *   (cf. CLAUDE.md identity (o) / guardian (dd) 도 동일 cross-cutting 보류)
 */
@Component
class TicketIssueTask(
    private val service: WarrantyExemptionTicketService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 0 1 * *", zone = "Asia/Seoul")
    fun issueMonthly() {
        val result = service.issueMonthlyBatch()
        log.info(
            "[TicketIssueTask] monthly batch fired — trust={}, excellent={}",
            result.trustIssued,
            result.excellentIssued,
        )
    }
}
