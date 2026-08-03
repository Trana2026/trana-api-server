package com.trana.contract.service

import com.trana.contract.entity.ContractStatus
import com.trana.contract.repository.ContractRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * SHARED / RECEIVER_SIGNED 상태 계약의 만료 처리 + 만료 30분 전 경고.
 *
 * 매 1분 실행:
 * 1) 만료(72h 초과) → EXPIRED 전이 + 삭제 알림톡(요청자 UJ_9527 / 미서명자 UJ_9529)
 * 2) 만료 30분 전(71.5h 초과, 경고 미발송) → 경고 알림톡(UJ_9524) + expiryWarnedAt 마킹
 *
 * 순서상 만료를 먼저 처리 → 72h 초과분은 EXPIRED 로 빠져 경고 대상(status IN)에서 자동 제외.
 * 알림톡은 best-effort(발송 실패해도 상태 전이 유지).
 * ShedLock 미도입 (N≥2 배포 시 도입 필요, cross-cutting #183).
 */
@Component
@Transactional
class ContractExpiryTask(
    private val contractRepository: ContractRepository,
    private val alimtalkDispatcher: ContractAlimtalkDispatcher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    fun run() {
        val now = Instant.now()
        expireOverdue(now)
        warnNearExpiry(now)
    }

    private fun expireOverdue(now: Instant) {
        val targets =
            contractRepository.findAllByStatusInAndStatusUpdatedAtBefore(
                statuses = EXPIRY_STATUSES,
                threshold = now.minus(EXPIRY_DURATION),
            )
        if (targets.isEmpty()) return
        targets.forEach { contract ->
            alimtalkDispatcher.sendExpiryDeleted(contract)
            contract.markExpired()
        }
        log.info("[CONTRACT_EXPIRY] expired {} contracts", targets.size)
    }

    private fun warnNearExpiry(now: Instant) {
        val targets =
            contractRepository.findAllByStatusInAndStatusUpdatedAtBeforeAndExpiryWarnedAtIsNull(
                statuses = EXPIRY_STATUSES,
                threshold = now.minus(WARNING_THRESHOLD),
            )
        if (targets.isEmpty()) return
        targets.forEach { contract ->
            alimtalkDispatcher.sendExpiryWarning(contract)
            contract.markExpiryWarned()
        }
        log.info("[CONTRACT_EXPIRY] warned {} contracts (30m before expiry)", targets.size)
    }

    companion object {
        private val EXPIRY_DURATION: Duration = Duration.ofHours(72)

        /** 만료 30분 전 = 71.5h 경과 시점 (statusUpdatedAt 기준). */
        private val WARNING_THRESHOLD: Duration = Duration.ofMinutes(72 * 60 - 30)
        private val EXPIRY_STATUSES: List<ContractStatus> =
            listOf(ContractStatus.SHARED, ContractStatus.RECEIVER_SIGNED)
    }
}
