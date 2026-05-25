package com.trana.contract.repository

import com.trana.contract.entity.ContractAttachment
import org.springframework.data.jpa.repository.JpaRepository

interface ContractAttachmentRepository : JpaRepository<ContractAttachment, Long> {
    /** UI 순서대로 전체 첨부 조회 (상세 화면). */
    fun findAllByContractIdOrderBySortOrderAsc(contractId: Long): List<ContractAttachment>

    /** 7장 제한 검사 — 등록 전 count. */
    fun countByContractId(contractId: Long): Long

    /** 목록 화면 thumbnail — sort_order 가장 작은 1장. */
    fun findFirstByContractIdOrderBySortOrderAsc(contractId: Long): ContractAttachment?
}
