package com.trana.contract.repository

import com.trana.contract.entity.ContractRevisionRequest
import org.springframework.data.repository.Repository

/**
 * 수정 요청 WORM Repository.
 *
 * 의도적으로 `Repository<T, ID>` 만 상속 — delete/saveAndFlush 미노출.
 * audit 보존 (insert-only).
 */
interface ContractRevisionRequestRepository : Repository<ContractRevisionRequest, Long> {
    fun save(request: ContractRevisionRequest): ContractRevisionRequest

    fun findAllByContractIdOrderByRequestedAtDesc(contractId: Long): List<ContractRevisionRequest>
}
