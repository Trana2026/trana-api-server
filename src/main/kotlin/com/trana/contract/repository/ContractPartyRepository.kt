package com.trana.contract.repository

import com.trana.contract.entity.ContractParty
import com.trana.contract.entity.PartyType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

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

    /**
     * 여러 계약에 대한 본인의 party 정보 일괄 조회 (목록 화면용).
     *
     * 사용: listMine — 본인이 관여한 contract 들의 partyType (myRole) 한 번에 조회.
     * 빈 리스트면 빈 결과 반환 (IN () 빈 set 안전).
     */
    fun findAllByUserIdAndContractIdIn(
        userId: Long,
        contractIds: Collection<Long>,
    ): List<ContractParty>

    /**
     * 특정 user 가 party 인 COMPLETED 계약 수 (RiskSignals tradeCount).
     */
    @Query(
        """
          SELECT COUNT(cp) FROM ContractParty cp
          WHERE cp.userId = :userId
            AND cp.contractId IN (
                SELECT c.id FROM Contract c WHERE c.status = com.trana.contract.entity.ContractStatus.COMPLETED
            )
          """,
    )
    fun countCompletedByUserId(
        @Param("userId") userId: Long,
    ): Long
}
