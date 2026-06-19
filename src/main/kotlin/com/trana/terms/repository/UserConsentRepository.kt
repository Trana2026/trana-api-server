package com.trana.terms.repository

import com.trana.terms.entity.ConsentContextType
import com.trana.terms.entity.UserConsent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserConsentRepository : JpaRepository<UserConsent, Long> {
    /** 가입 세션 TTL 검증용 (가장 오래된 row의 agreedAt 기준). */
    fun findFirstBySignupSessionIdOrderByAgreedAtAsc(signupSessionId: UUID): UserConsent?

    /** Compare SUCCESS 시 user_id 백필용 (해당 세션의 모든 동의 row). */
    fun findAllBySignupSessionId(signupSessionId: UUID): List<UserConsent>

    /** 보호자 동의 idempotency 체크용 (refactor ee) — 같은 토큰의 기존 동의 row 전체. */
    fun findAllByGuardianLinkToken(guardianLinkToken: String): List<UserConsent>

    /** 마이페이지 — 본인의 가입 약관 동의 내역 (최신순). */
    fun findAllByUserIdAndContextTypeOrderByAgreedAtDesc(
        userId: Long,
        contextType: ConsentContextType,
    ): List<UserConsent>
}
