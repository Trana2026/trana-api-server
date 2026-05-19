package com.trana.identity.service

import com.trana.guardian.GuardianException
import com.trana.guardian.entity.GuardianLink
import com.trana.guardian.service.GuardianLinkService
import com.trana.identity.IdentityException
import com.trana.identity.entity.IdCardVerifySession
import com.trana.identity.entity.IdentityVerification
import com.trana.identity.entity.VerificationStatus
import com.trana.identity.repository.IdentityVerificationRepository
import com.trana.terms.service.ConsentService
import com.trana.user.entity.AgeGroup
import com.trana.user.entity.User
import com.trana.user.service.UserService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.util.UUID

/**
 * KYC 흐름의 상태 조회/검증 공통 헬퍼.
 *
 * 두 service에서 공유:
 * - KycSessionService (recognize/verify/recordPhone)
 * - KycSignupService  (compareFaces)
 *
 * 책임: "현재 흐름이 진행 가능한 상태인지" 판단 → 적절한 IdentityException으로 변환.
 * 트랜잭션 자체는 호출자(service)에서 관리 — 이 클래스는 readOnly lookup만.
 */
@Component
class KycStateLookup(
    private val sessionService: IdCardVerifySessionService,
    private val verificationRepository: IdentityVerificationRepository,
    private val consentService: ConsentService,
    private val guardianLinkService: GuardianLinkService,
    private val userService: UserService,
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

    /**
     * 활성 세션 로드. 없음 / 만료 시 적절한 예외.
     * @throws IdentityException.SessionNotFound / SessionExpired
     */
    @Transactional(readOnly = true)
    fun loadActiveSession(requestId: String): IdCardVerifySession =
        sessionService.findActive(requestId) ?: throw sessionMissingException(requestId)

    /**
     * 활성 guardian_link 로드 (보호자 KYC 진입점).
     * @throws GuardianException.LinkNotFound / LinkInvalid
     */
    @Transactional(readOnly = true)
    fun loadActiveGuardianLink(token: String): GuardianLink = guardianLinkService.findActive(token)

    /**
     * 보호자 KYC subject 검증 — link.userId가 가리키는 미성년자가 유효한지.
     * @throws GuardianException.NotMinor / AlreadyVerified
     */
    @Transactional(readOnly = true)
    fun loadSubjectMinor(userId: Long): User {
        val user = userService.getById(userId)
        if (user.ageGroup != AgeGroup.MINOR) throw GuardianException.NotMinor(userId)
        if (user.guardianVerifiedAt != null) throw GuardianException.AlreadyVerified(userId)
        return user
    }

    /**
     * PENDING IdentityVerification 로드. 세션이 살아있다면 DB 불변식상 존재.
     * 없으면 IllegalStateException (불변식 위반).
     */
    @Transactional(readOnly = true)
    fun loadPendingVerification(requestId: String): IdentityVerification {
        val verification =
            checkNotNull(verificationRepository.findByNcpDocumentRequestId(requestId)) {
                "IdentityVerification missing for requestId=$requestId (세션 lookup 후 호출 전제)"
            }
        check(verification.status == VerificationStatus.PENDING) {
            "verification.status=${verification.status} (PENDING만 진행 가능)"
        }
        return verification
    }

    private fun sessionMissingException(requestId: String): IdentityException {
        val verificationExists = verificationRepository.findByNcpDocumentRequestId(requestId) != null
        return if (verificationExists) {
            IdentityException.SessionExpired(requestId)
        } else {
            IdentityException.SessionNotFound(requestId)
        }
    }

    /**
     * 보호자 후보가 성인(만 19세 이상)인지 검증 — OCR 결과 birthDate 기준.
     * @throws IdentityException.NotAdult
     * @throws IdentityException.NotAdult
     */
    fun requireAdult(
        birthDate: LocalDate,
        identifierHash: String,
    ) {
        val age = Period.between(birthDate, LocalDate.now()).years
        if (age < ADULT_AGE) throw IdentityException.NotAdult(identifierHash)
    }

    companion object {
        private val SIGNUP_SESSION_TTL: Duration = Duration.ofMinutes(30)
        private const val ADULT_AGE = 19
    }
}
