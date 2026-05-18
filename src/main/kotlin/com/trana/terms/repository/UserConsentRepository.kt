package com.trana.terms.repository

import com.trana.terms.entity.UserConsent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserConsentRepository : JpaRepository<UserConsent, Long> {
    /** 가입 세션 TTL 검증용 (가장 오래된 row의 agreedAt 기준). */
    fun findFirstBySignupSessionIdOrderByAgreedAtAsc(signupSessionId: UUID): UserConsent?

    /** Compare SUCCESS 시 user_id 백필용 (해당 세션의 모든 동의 row). */
    fun findAllBySignupSessionId(signupSessionId: UUID): List<UserConsent>
}
