package com.trana.contract.service

import com.trana.contract.entity.Contract
import com.trana.contract.repository.ContractPartyRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 계약의 상대편 user id 찾기 (refactor #104 공통화).
 *
 * - caller 가 creator 면 → contract_parties 에서 다른 user (수신자)
 * - caller 가 수신자(party) 면 → contract.creatorUserId
 * - 수신자가 아직 accept 안 한 단계면 → null (party 1개)
 *
 * 호출처: RiskSignalsCalculator / DisputeService / ContractCancellationService
 */
@Component
class CounterpartyResolver(
    private val contractPartyRepository: ContractPartyRepository,
) {
    @Transactional(readOnly = true)
    fun resolveCounterpartUserId(
        contract: Contract,
        callerUserId: Long,
    ): Long? {
        if (contract.creatorUserId != callerUserId) {
            return contract.creatorUserId
        }
        return contractPartyRepository
            .findAllByContractId(contract.id!!)
            .firstOrNull { it.userId != callerUserId }
            ?.userId
    }
}
