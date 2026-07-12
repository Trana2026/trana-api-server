package com.trana.contract.service

import com.trana.contract.MinorDisclosureException
import com.trana.contract.entity.Contract
import com.trana.contract.entity.MinorDisclosureConfirmation
import com.trana.contract.repository.MinorDisclosureConfirmationRepository
import com.trana.user.entity.AgeGroup
import com.trana.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 미성년자 계약 상대방(성인)의 서명 전 위험 고지 확인 관리.
 *
 * 흐름:
 * - 프론트: 고지 화면 표시 → 확인 클릭 → confirm() 호출 → row upsert
 * - 서명 endpoint 진입 시 requireConfirmedIfMinorCounterparty() 로 게이트 검증
 * - 미성년자 계약인데 상대측 확인 없으면 NotConfirmed 예외 (403)
 */
@Service
@Transactional
class MinorDisclosureConfirmationService(
    private val repository: MinorDisclosureConfirmationRepository,
    private val userRepository: UserRepository,
    private val counterpartyResolver: CounterpartyResolver,
) {
    /**
     * 위험 고지 확인 UPSERT.
     * - 상대방이 미성년자가 아니면 CounterpartyNotMinor 예외 (409)
     * - 재확인 시 기존 row 삭제 후 새로 저장 (UNIQUE 회피 + 최신 IP/UA/버전 반영)
     */
    fun confirm(
        contract: Contract,
        partyUserId: Long,
        disclosedAt: Instant,
        ip: String?,
        userAgent: String?,
    ): MinorDisclosureConfirmation {
        val counterpartId =
            counterpartyResolver.resolveCounterpartUserId(contract, partyUserId)
                ?: throw MinorDisclosureException.CounterpartyNotMinor(contract.publicCode)
        val counterpart =
            userRepository.findById(counterpartId).orElseThrow {
                MinorDisclosureException.CounterpartyNotMinor(contract.publicCode)
            }
        if (counterpart.ageGroup != AgeGroup.MINOR) {
            throw MinorDisclosureException.CounterpartyNotMinor(contract.publicCode)
        }

        repository.findByContractIdAndPartyUserId(contract.id!!, partyUserId)?.let {
            repository.delete(it)
            repository.flush()
        }
        return repository.save(
            MinorDisclosureConfirmation(
                contractId = contract.id!!,
                partyUserId = partyUserId,
                templateVersion = MinorDisclosureTemplate.LATEST_VERSION,
                disclosedAt = disclosedAt,
                ip = ip,
                userAgent = userAgent,
            ),
        )
    }

    /**
     * 서명 endpoint 사전 게이트.
     * viewer 의 상대(counterpart)가 미성년자이면 viewer 는 위험 고지 확인이 있어야 함.
     * 상대가 미성년자 아니거나 counterpart 미확정 (초기 단계) 이면 no-op.
     */
    @Transactional(readOnly = true)
    fun requireConfirmedIfMinorCounterparty(
        contract: Contract,
        viewerUserId: Long,
    ) {
        val counterpart =
            counterpartyResolver
                .resolveCounterpartUserId(contract, viewerUserId)
                ?.let { userRepository.findById(it).orElse(null) }
        val isMinorCounterparty = counterpart?.ageGroup == AgeGroup.MINOR
        if (isMinorCounterparty && !repository.existsByContractIdAndPartyUserId(contract.id!!, viewerUserId)) {
            throw MinorDisclosureException.NotConfirmed(contract.publicCode)
        }
    }
}
