package com.trana.dispute.repository

import com.trana.dispute.entity.DisputeRecord
import com.trana.dispute.entity.DisputeStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface DisputeRecordRepository : JpaRepository<DisputeRecord, Long> {
    /**
     * 신고 취소 권한 + 활성 상태 + URL contractId 일치 한 번에 검증.
     * null 이면 NotFound (id 추측 enumeration 방지 — 권한/상태 구분 X).
     */
    fun findFirstByContractIdAndIdAndReporterUserIdAndStatus(
        contractId: Long,
        id: Long,
        reporterUserId: Long,
        status: DisputeStatus,
    ): DisputeRecord?

    /**
     * 계약 단위 신고 목록 (최신순).
     */
    fun findByContractIdOrderByReportedAtDesc(contractId: Long): List<DisputeRecord>

    /**
     * 본인 활성 신고 중복 사전 차단 (partial UNIQUE race 친절 변환).
     */
    fun existsByContractIdAndReporterUserIdAndStatus(
        contractId: Long,
        reporterUserId: Long,
        status: DisputeStatus,
    ): Boolean

    /**
     * 활성 신고 카운트 — 특정 신고 제외.
     * cancelByReporter 직후 다른 활성 신고 존재 여부 확인 (JPA flush 의존 회피).
     */
    fun countByContractIdAndStatusAndIdNot(
        contractId: Long,
        status: DisputeStatus,
        id: Long,
    ): Long

    /**
     * 특정 user 가 다른 계약에서 신고된 적 있는지 (활성 REPORTED 만, riskSignals 산출용).
     * - 본인이 creator 또는 contract_parties 멤버인 계약 검사
     * - 본인이 신고자가 아닌 row 만 (자기가 신고당한 신고)
     * - CANCELLED_BY_REPORTER 제외 (활성만)
     */
    @Query(
        value = """
          SELECT EXISTS (
              SELECT 1 FROM dispute_records dr
              JOIN contracts c ON c.id = dr.contract_id
              WHERE dr.reporter_user_id <> :userId
                AND dr.status = 'REPORTED'
                AND (
                  c.creator_user_id = :userId
                  OR EXISTS (
                      SELECT 1 FROM contract_parties cp
                      WHERE cp.contract_id = c.id AND cp.user_id = :userId
                  )
                )
          )
      """,
        nativeQuery = true,
    )
    fun existsReportAgainstUser(
        @org.springframework.data.repository.query.Param("userId") userId: Long,
    ): Boolean

    /**
     * 특정 user 가 신고당한 총 dispute 수 — 자신이 신고자가 아닌 계약의 dispute count (RiskSignals disputeCount).
     */
    @Query(
        """
          SELECT COUNT(d) FROM DisputeRecord d
          WHERE d.reporterUserId <> :userId
            AND d.contractId IN (
                SELECT cp.contractId FROM ContractParty cp WHERE cp.userId = :userId
            )
          """,
    )
    fun countReportedAgainstUser(
        @Param("userId") userId: Long,
    ): Long

    /**
     * 위 중 resolution=FRAUD_CONFIRMED 만 (RiskSignals confirmedReportCount).
     */
    @Query(
        """
          SELECT COUNT(d) FROM DisputeRecord d
          WHERE d.reporterUserId <> :userId
            AND d.resolution = com.trana.dispute.entity.DisputeResolution.FRAUD_CONFIRMED
            AND d.contractId IN (
                SELECT cp.contractId FROM ContractParty cp WHERE cp.userId = :userId
            )
          """,
    )
    fun countConfirmedReportedAgainstUser(
        @Param("userId") userId: Long,
    ): Long

    /**
     * 3년 이상 resolve 된 dispute cleanup — 사기 확인 (FRAUD_CONFIRMED) 은 영구 보존.
     * PENDING 은 삭제 X (미해결 유지).
     */
    @Modifying
    @Query(
        """
          DELETE FROM DisputeRecord d
          WHERE d.resolvedAt IS NOT NULL
            AND d.resolvedAt < :threshold
            AND d.resolution <> com.trana.dispute.entity.DisputeResolution.FRAUD_CONFIRMED
          """,
    )
    fun deleteResolvedBeforeAndResolutionNotConfirmed(
        @Param("threshold") threshold: Instant,
    ): Int
}
