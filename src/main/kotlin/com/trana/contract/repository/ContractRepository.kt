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

    /**
     * 본인이 관여한 모든 계약 목록 — creator OR contract_parties 멤버.
     *
     * 사용: GET /v1/contracts — 본인이 만든 것 + 수신자로 받은 것 모두.
     * - status null = 전체. 정렬: updatedAt DESC.
     * - query null/blank = 검색 무효 / 값 있으면 title ILIKE %query% (대소문자 무시).
     *   title NULL 인 IN_PROGRESS contract 는 query 있으면 자동 제외.
     */
    @Query(
        """
            SELECT c FROM Contract c
            WHERE c.deletedAt IS NULL
              AND (:status IS NULL OR c.status = :status)
              AND (
                :query IS NULL
                OR (c.title IS NOT NULL AND LOWER(c.title) LIKE LOWER(CONCAT('%', :query, '%')))
              )
              AND EXISTS (
                SELECT 1 FROM ContractParty p
                WHERE p.contractId = c.id
                  AND p.userId = :userId
              )
            ORDER BY c.updatedAt DESC
            """,
    )
    fun findAllByPartyUserId(
        @Param("userId") userId: Long,
        @Param("status") status: ContractStatus?,
        @Param("query") query: String?,
    ): List<Contract>
}
