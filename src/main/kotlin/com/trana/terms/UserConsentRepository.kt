package com.trana.terms

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserConsentRepository : JpaRepository<UserConsent, Long> {
    fun findByUserId(userId: Long): List<UserConsent>

    fun findBySignupSessionId(signupSessionId: UUID): List<UserConsent>
}
