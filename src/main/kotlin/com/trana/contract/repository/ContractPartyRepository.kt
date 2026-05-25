package com.trana.contract.repository

import com.trana.contract.entity.ContractParty
import com.trana.contract.entity.PartyType
import org.springframework.data.jpa.repository.JpaRepository

interface ContractPartyRepository : JpaRepository<ContractParty, Long> {
    /** 계약의 모든 당사자 (W4 = 1명, W5 = 2명). */
    fun findAllByContractId(contractId: Long): List<ContractParty>

    /** 역할별 단건 조회 — (contractId, partyType) UNIQUE 보장. */
    fun findByContractIdAndPartyType(
        contractId: Long,
        partyType: PartyType,
    ): ContractParty?

    /** 권한 검사 — 이 user 가 계약 당사자인지. */
    fun findFirstByContractIdAndUserId(
        contractId: Long,
        userId: Long,
    ): ContractParty?
}
