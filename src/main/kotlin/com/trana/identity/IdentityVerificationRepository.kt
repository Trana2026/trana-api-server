package com.trana.identity

import org.springframework.data.jpa.repository.JpaRepository

interface IdentityVerificationRepository : JpaRepository<IdentityVerification, Long> {
    /**
     * 중복 가입 lookup — 같은 식별번호로 SUCCESS인 KYC 이력이 이미 있는지.
     */
    fun existsByIdentifierHashAndStatus(
        identifierHash: String,
        status: IdentityVerificationStatus,
    ): Boolean

    /**
     * NCP Document API requestId 로 PENDING record 조회 (Verify/Compare 단계 update용).
     */
    fun findByNcpDocumentRequestId(ncpDocumentRequestId: String): IdentityVerification?
}
