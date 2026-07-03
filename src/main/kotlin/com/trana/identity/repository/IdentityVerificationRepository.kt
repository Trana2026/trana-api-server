package com.trana.identity.repository

import com.trana.identity.entity.IdentityVerification
import com.trana.identity.entity.VerificationPurpose
import com.trana.identity.entity.VerificationStatus
import org.springframework.data.jpa.repository.JpaRepository

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
}
