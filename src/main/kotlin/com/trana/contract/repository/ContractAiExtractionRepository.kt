package com.trana.contract.repository

import com.trana.contract.entity.ContractAiExtraction
import org.springframework.data.jpa.repository.JpaRepository

interface ContractAiExtractionRepository : JpaRepository<ContractAiExtraction, Long> {
    /** 가장 최근 추출 결과 (사용자가 다시 추출하면 새 row, prefill 은 최신만 노출). */
    fun findFirstByContractIdOrderByExtractedAtDesc(contractId: Long): ContractAiExtraction?

    /** audit 용 전체 시도 이력. */
    fun findAllByContractIdOrderByExtractedAtDesc(contractId: Long): List<ContractAiExtraction>
}
