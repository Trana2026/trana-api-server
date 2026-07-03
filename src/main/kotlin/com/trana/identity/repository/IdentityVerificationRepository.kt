package com.trana.identity.repository

import com.trana.identity.entity.IdentityVerification
import com.trana.identity.entity.VerificationPurpose
import com.trana.identity.entity.VerificationStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface IdentityVerificationRepository : JpaRepository<IdentityVerification, Long> {
    /**
     * PASS clientTxId 매핑 verification 조회 — return endpoint 에서 mobileOK 응답 복호화 후 사용.
     */
    fun findByClientTxId(clientTxId: String): IdentityVerification?

    /**
     * 가입 단계 보호자 KYC SUCCESS row 조회.
     *
     * 계약 단계 보호자 동의 (web 단순 동의) 시 미성년의 가입 단계 보호자 ID 자동 매핑용.
     * 한 미성년 user 는 가입 단계에서 1명의 보호자만 매핑 (markGuardianVerified 1회) → first 1개로 충분.
     */
    fun findFirstBySubjectUserIdAndPurposeAndStatus(
        subjectUserId: Long,
        purpose: VerificationPurpose,
        status: VerificationStatus,
    ): IdentityVerification?

    /**
     * dev 리셋용 — 특정 미성년자에 대한 모든 GUARDIAN verification 삭제.
     *
     * DevTokenController.resetGuardianVerification 에서 호출.
     * subject_user_id 는 GUARDIAN purpose row 만 채워지므로 purpose 조건 불필요.
     * production 로직에서는 호출 X.
     * @return 삭제된 row 수
     */
    @Modifying
    @Query("DELETE FROM IdentityVerification v WHERE v.subjectUserId = :subjectUserId")
    fun deleteAllBySubjectUserId(
        @Param("subjectUserId") subjectUserId: Long,
    ): Int
}
