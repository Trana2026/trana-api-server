package com.trana.contract.repository

import com.trana.contract.entity.ContractStatusLog
import org.springframework.data.repository.Repository

/**
 * 계약 상태 전이 WORM 로그 Repository.
 *
 * 의도적으로 `Repository<T, ID>` 만 상속 — delete'*'/saveAndFlush 등 미노출.
 * JpaRepository 면 delete*() 가 자동 노출되어 실수로 audit 삭제 가능 (WORM 위반).
 * 필요한 메서드만 명시 선언.
 */
interface ContractStatusLogRepository : Repository<ContractStatusLog, Long> {
    fun save(log: ContractStatusLog): ContractStatusLog

    fun findAllByContractIdOrderByChangedAtAsc(contractId: Long): List<ContractStatusLog>
}
