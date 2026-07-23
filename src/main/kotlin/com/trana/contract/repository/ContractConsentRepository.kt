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

    /** 마이페이지 — 사용자의 특정 약관(AI 국외이전 등) 최신 동의 1건. idx_contract_consents_user_term 활용. */
    fun findFirstByUserIdAndTermIdOrderByConsentedAtDesc(
        userId: Long,
        termId: Long,
    ): ContractConsent?
}
