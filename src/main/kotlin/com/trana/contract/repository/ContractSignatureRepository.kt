package com.trana.contract.repository

import com.trana.contract.entity.ContractSignature
import com.trana.contract.entity.PartyType
import org.springframework.data.repository.Repository

/**
 * 계약 서명 WORM Repository.
 *
 * 의도적으로 `Repository<T, ID>` 만 상속 — delete/saveAndFlush 미노출.
 * audit 보존 위해 insert-only.
 */
interface ContractSignatureRepository : Repository<ContractSignature, Long> {
    fun save(signature: ContractSignature): ContractSignature

    fun findAllByContractIdOrderBySignedAtAsc(contractId: Long): List<ContractSignature>

    fun findByContractIdAndPartyType(
        contractId: Long,
        partyType: PartyType,
    ): ContractSignature?
}
