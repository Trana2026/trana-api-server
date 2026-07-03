package com.trana.identity.service

import com.trana.identity.IdentityException
import com.trana.terms.service.ConsentService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * signupSession TTL 검증 helper.
 *
 * PASS 흐름 (PassSignupService) 이 재사용 — 약관 동의 후 30분 안에 PASS 표준창 진입 강제.
 */
@Component
class KycStateLookup(
    private val consentService: ConsentService,
) {
    /**
     * 가입 세션(약관 동의) TTL 30분 검증.
     * @throws IdentityException.SignupSessionNotFound / SignupSessionExpired
     */
    @Transactional(readOnly = true)
    fun validateSignupSession(signupSessionId: UUID) {
        val consent =
            consentService.findFirstBySignupSessionId(signupSessionId)
                ?: throw IdentityException.SignupSessionNotFound(signupSessionId)
        val agreedAt =
            checkNotNull(consent.agreedAt) { "UserConsent.agreedAt is null (@CreationTimestamp invariant 위반)" }
        if (Instant.now().isAfter(agreedAt.plus(SIGNUP_SESSION_TTL))) {
            throw IdentityException.SignupSessionExpired(signupSessionId)
        }
    }

    companion object {
        private val SIGNUP_SESSION_TTL: Duration = Duration.ofMinutes(30)
    }
}
