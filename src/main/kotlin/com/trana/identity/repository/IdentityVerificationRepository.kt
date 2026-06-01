package com.trana.identity.repository

import com.trana.identity.entity.IdentityVerification
import com.trana.identity.entity.VerificationPurpose
import com.trana.identity.entity.VerificationStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface IdentityVerificationRepository : JpaRepository<IdentityVerification, Long> {
    /**
     * 같은 세션의 진행 중 verification 조회 (Verify/Phone/Compare step에서 lookup).
     * SIGNUP 흐름 전용 — userId가 아직 없는 단계에서 signupSessionId로 매칭.
     */
    fun findFirstBySignupSessionIdAndStatus(
        signupSessionId: UUID,
        status: VerificationStatus,
    ): IdentityVerification?

    /**
     * 보호자 KYC 재진입 시 기존 PENDING verification 조회 (token 매칭).
     * KycGuardianService.recognizeIdCard 시작부에서 IdentitySessionPurger 호출 전에 사용.
     */
    fun findFirstByGuardianLinkTokenAndStatus(
        guardianLinkToken: String,
        status: VerificationStatus,
    ): IdentityVerification?

    /**
     * NCP requestId로 lookup (Verify/Compare 호출 시 step별 record 찾기).
     */
    fun findByNcpDocumentRequestId(ncpDocumentRequestId: String): IdentityVerification?

    /**
     * 중복 가입 방지 — 같은 identifierHash의 SUCCESS record 존재 여부.
     */
    fun existsByIdentifierHashAndStatus(
        identifierHash: String,
        status: VerificationStatus,
    ): Boolean

    /**
     * 본인 KYC 중복 가입 차단 — ACTIVE user의 SUCCESS verification만 검사.
     *
     * 기존 existsByIdentifierHashAndStatus와 차이:
     * - WITHDRAWN user의 SUCCESS는 제외 → 같은 신분증으로 재가입 허용
     * - 보호자 KYC 차단은 별도 정책 (KycGuardianService는 기존 메서드 사용)
     */
    @Query(
        """
      SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END
      FROM IdentityVerification v
      WHERE v.identifierHash = :hash
        AND v.status = com.trana.identity.entity.VerificationStatus.SUCCESS
        AND EXISTS (
            SELECT 1 FROM com.trana.user.entity.User u
            WHERE u.id = v.userId
            AND u.status = com.trana.user.entity.UserStatus.ACTIVE
        )
      """,
    )
    fun existsActiveSuccessByIdentifierHash(
        @Param("hash") hash: String,
    ): Boolean

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
     * Cleanup task용 — id_card_verify_session 만료 시 PENDING verification 같이 정리.
     * SUCCESS/FAILED는 audit 가치라 보존.
     * @return 삭제된 row 수 (0 = 매칭 없음)
     */
    fun deleteByNcpDocumentRequestIdAndStatus(
        ncpDocumentRequestId: String,
        status: VerificationStatus,
    ): Int
}
