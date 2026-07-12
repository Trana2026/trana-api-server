package com.trana.contract.repository

import com.trana.contract.entity.MinorDisclosureConfirmation
import org.springframework.data.jpa.repository.JpaRepository

interface MinorDisclosureConfirmationRepository : JpaRepository<MinorDisclosureConfirmation, Long> {
    /**
     * 서명 endpoint 사전 게이트용 존재 확인 — 미성년자 계약이면 상대측(party) 확인이 있어야 서명 가능.
     */
    fun existsByContractIdAndPartyUserId(
        contractId: Long,
        partyUserId: Long,
    ): Boolean

    /**
     * 재확인 UPSERT 를 위한 기존 row 조회.
     */
    fun findByContractIdAndPartyUserId(
        contractId: Long,
        partyUserId: Long,
    ): MinorDisclosureConfirmation?
}
