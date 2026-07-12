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
 * SHARED / RECEIVER_SIGNED 상태 72h 초과 자동 만료.
 *
 * 배경:
 * - 성인이 위험 고지 확인 후 서명했는데 미성년 상대가 최종 서명 미룰 시 성인은 철회권 없음 → 자동 만료로 exit
 * - 일반 계약도 오랜 방치 시 관계자에게 stuck 상태 해제 기회 제공
 *
 * - 매 시간 정각 (KST) 실행
 * - ShedLock 미도입 (N≥2 배포 시 도입 필요, cross-cutting #183)
 */
@Component
@Transactional
class ContractExpiryTask(
    private val contractRepository: ContractRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    fun run() {
        val threshold = Instant.now().minus(EXPIRY_DURATION)
        val targets =
            contractRepository.findAllByStatusInAndStatusUpdatedAtBefore(
                statuses = EXPIRY_STATUSES,
                threshold = threshold,
            )
        if (targets.isEmpty()) {
            log.info("[CONTRACT_EXPIRY] no targets — threshold={}", threshold)
            return
        }
        targets.forEach { it.markExpired() }
        log.info(
            "[CONTRACT_EXPIRY] expired {} contracts (threshold={})",
            targets.size,
            threshold,
        )
    }

    companion object {
        private val EXPIRY_DURATION: Duration = Duration.ofHours(72)
        private val EXPIRY_STATUSES: List<ContractStatus> =
            listOf(ContractStatus.SHARED, ContractStatus.RECEIVER_SIGNED)
    }
}
