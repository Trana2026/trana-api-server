package com.trana.contract.repository

import com.trana.contract.entity.ContractConsent
import org.springframework.data.repository.Repository

/**
 * 계약 도메인 약관 동의 WORM Repository.
 *
 * 의도적으로 `Repository<T, ID>` 만 상속 — delete/saveAndFlush 미노출.
 * audit 보존 위해 insert-only.
 */
interface ContractConsentRepository : Repository<ContractConsent, Long> {
    fun save(consent: ContractConsent): ContractConsent

    fun findAllByContractIdOrderByConsentedAtAsc(contractId: Long): List<ContractConsent>

    fun findAllByContractIdAndUserIdOrderByConsentedAtAsc(
        contractId: Long,
        userId: Long,
    ): List<ContractConsent>
}
