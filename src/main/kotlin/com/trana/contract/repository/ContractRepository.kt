package com.trana.contract.repository

import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ContractRepository : JpaRepository<Contract, Long> {
    /** publicCode 로 단건 조회 (soft delete 제외). */
    fun findByPublicCodeAndDeletedAtIsNull(publicCode: String): Contract?

    /** publicCode 충돌 검사 (nanoid 생성 시 재시도 판단용). */
    fun existsByPublicCode(publicCode: String): Boolean

    /**
     * 사용자별 계약 목록 (soft delete 제외, 최신순).
     * status null = 전체 / 지정하면 해당 상태만.
     */
    @Query(
        """
          SELECT c FROM Contract c
          WHERE c.creatorUserId = :userId
            AND c.deletedAt IS NULL
            AND (:status IS NULL OR c.status = :status)
          ORDER BY c.updatedAt DESC
          """,
    )
    fun findAllByCreator(
        @Param("userId") userId: Long,
        @Param("status") status: ContractStatus?,
    ): List<Contract>
}
