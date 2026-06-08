package com.trana.contract.repository

import com.trana.contract.entity.CancellationStatus
import com.trana.contract.entity.ContractCancellationRequest
import org.springframework.data.jpa.repository.JpaRepository

interface ContractCancellationRequestRepository : JpaRepository<ContractCancellationRequest, Long> {
    /**
     * 본인이 이미 활성 요청 보유 중인지 사전 차단 (partial UNIQUE race 친절 변환).
     */
    fun existsByContractIdAndStatus(
        contractId: Long,
        status: CancellationStatus,
    ): Boolean

    /**
     * 활성 취소 요청 조회 (상대 측 confirm 시 사용).
     */
    fun findFirstByContractIdAndStatus(
        contractId: Long,
        status: CancellationStatus,
    ): ContractCancellationRequest?

    /**
     * 계약 단위 취소요청 목록 (최신순).
     */
    fun findByContractIdOrderByRequestedAtDesc(contractId: Long): List<ContractCancellationRequest>
}
