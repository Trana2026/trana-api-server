package com.trana.identity.repository

import com.trana.identity.entity.IdentityVerification
import com.trana.identity.entity.VerificationStatus
import org.springframework.data.jpa.repository.JpaRepository
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
}
