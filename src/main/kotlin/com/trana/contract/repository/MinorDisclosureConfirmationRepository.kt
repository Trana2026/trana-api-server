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

    /**
     * PDF 별지 렌더용 — 계약의 고지 확인서 조회.
     * 미성년 계약은 성인 상대방 1명만 확인하므로 사실상 ≤1건.
     */
    fun findAllByContractId(contractId: Long): List<MinorDisclosureConfirmation>
}
