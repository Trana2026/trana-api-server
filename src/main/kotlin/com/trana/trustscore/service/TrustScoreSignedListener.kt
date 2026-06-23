package com.trana.trustscore.service

import com.trana.contract.entity.ContractStatus
import com.trana.contract.entity.PartyType
import com.trana.contract.repository.ContractPartyRepository
import com.trana.contract.repository.ContractRepository
import com.trana.contract.service.ContractStatusChangedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * SIGNED 전이 시 양측 신뢰 점수 자동 적립 listener.
 *
 * @EventListener (synchronous) — ContractSigningService.creatorSign 의 tx 안에서 함께 commit.
 * 점수 적립 실패 = SIGNED 전이도 rollback (안전성 ↑, 점수 적용은 DB INSERT 2~3건이라 부담 X).
 * cf. ContractStatusLogListener 와 같은 패턴.
 *
 * 트리거:
 * - toStatus = SIGNED 만 처리 (다른 상태는 무시)
 * - 양측 +2 (BOTH_SIGNED)
 * - 판매자 warrantyPeriodDays > 0 시 추가 +3 (WARRANTY_PROVIDED)
 *
 * FCM 알림은 Phase 4 — 별도 listener (@TransactionalEventListener AFTER_COMMIT).
 */
@Component
class TrustScoreSignedListener(
    private val trustScoreService: TrustScoreService,
    private val contractRepository: ContractRepository,
    private val contractPartyRepository: ContractPartyRepository,
) {
    @EventListener
    fun handle(event: ContractStatusChangedEvent) {
        if (event.toStatus != ContractStatus.SIGNED) return

        val contract =
            contractRepository.findById(event.contractId).orElseThrow {
                IllegalStateException("계약 조회 실패 (contractId=${event.contractId})")
            }
        val parties = contractPartyRepository.findAllByContractId(event.contractId)
        val seller =
            parties.firstOrNull { it.partyType == PartyType.SELLER }
                ?: error("SIGNED 전이인데 SELLER 당사자 누락 (contractId=${event.contractId})")
        val buyer =
            parties.firstOrNull { it.partyType == PartyType.BUYER }
                ?: error("SIGNED 전이인데 BUYER 당사자 누락 (contractId=${event.contractId})")

        trustScoreService.applyBothSigned(
            sellerId = seller.userId,
            buyerId = buyer.userId,
            contractId = event.contractId,
            warrantyProvided = contract.warrantyPeriodDays > 0,
        )
    }
}
