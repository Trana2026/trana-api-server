package com.trana.contract.repository

import com.trana.contract.entity.ContractAiExtraction
import org.springframework.data.jpa.repository.JpaRepository

interface ContractAiExtractionRepository : JpaRepository<ContractAiExtraction, Long> {
    /** 가장 최근 추출 row (status 무관 — PENDING/SUCCESS/FAILED 모두 포함). 폴링 / latest 노출용. */
    fun findFirstByContractIdOrderByExtractedAtDesc(contractId: Long): ContractAiExtraction?

    /** audit 용 전체 시도 이력. */
    fun findAllByContractIdOrderByExtractedAtDesc(contractId: Long): List<ContractAiExtraction>

    /** 보안: 특정 contract 소속 row 만 조회 (다른 계약의 extractionId 폴링 차단). */
    fun findByIdAndContractId(
        id: Long,
        contractId: Long,
    ): ContractAiExtraction?
}
