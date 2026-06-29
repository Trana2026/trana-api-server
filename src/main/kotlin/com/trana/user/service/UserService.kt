package com.trana.user.service

import com.trana.audit.AuditEvent
import com.trana.audit.AuditLogger
import com.trana.auth.oauth.SocialProvider
import com.trana.common.util.TokenGenerator
import com.trana.trustscore.service.FraudUserHashService
import com.trana.user.UserException
import com.trana.user.entity.AgeGroup
import com.trana.user.entity.Gender
import com.trana.user.entity.SocialAccount
import com.trana.user.entity.User
import com.trana.user.entity.UserStatus
import com.trana.user.repository.SocialAccountRepository
import com.trana.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 사용자 도메인 서비스.
 *
 * - 미성년자 가입: findOrCreateBySocial (소셜 로그인 트리거, ageGroup=MINOR 명시)
 * - 성인 가입: createFromKyc (본인 KYC SUCCESS 시점 호출, ageGroup=ADULT 또는 KYC 결과 MINOR)
 */
@Service
@Transactional
class UserService(
    private val userRepository: UserRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val tokenGenerator: TokenGenerator,
    private val auditLogger: AuditLogger,
    private val fraudUserHashService: FraudUserHashService,
) {
    /**
     * 소셜 로그인: 기존 매핑이 있으면 ACTIVE user 반환, WITHDRAWN이면 신규 user 생성 (재가입).
     * 미성년자 가입 전용 흐름 — 호출 시 ageGroup=MINOR 명시.
     */
    fun findOrCreateBySocial(
        provider: SocialProvider,
        providerUserId: String,
        email: String? = null,
        name: String? = null,
        ageGroup: AgeGroup,
    ): User {
        val existing = socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)
        if (existing != null) {
            val existingUser =
                userRepository.findById(existing.userId).orElseThrow {
                    IllegalStateException(
                        "Orphan social_account: id=${existing.id}, userId=${existing.userId}",
                    )
                }
            if (existingUser.status == UserStatus.ACTIVE) {
                return existingUser
            }
            // WITHDRAWN: 기존 매핑 삭제 (unique 충돌 회피) → 신규 user 생성 (아래 흐름 계속)
            socialAccountRepository.delete(existing)
            socialAccountRepository.flush()
        }

        val newUser =
            User(
                publicCode = tokenGenerator.generatePublicCode(),
                email = email,
                name = name,
                ageGroup = ageGroup,
            )
        userRepository.save(newUser)
        val userId = checkNotNull(newUser.id) { "User id should be assigned after save" }

        socialAccountRepository.save(
            SocialAccount(
                userId = userId,
                provider = provider,
                providerUserId = providerUserId,
            ),
        )

        auditLogger.log(
            eventType = AuditEvent.USER_CREATED,
            actorUserId = userId,
            entityType = ENTITY_USER,
            entityId = userId,
            metadata =
                mapOf(
                    "source" to "SOCIAL",
                    "provider" to provider.name,
                    "ageGroup" to ageGroup.name,
                    "rejoined" to (existing != null),
                ),
        )
        return newUser
    }

    /**
     * 성인 가입 또는 KYC 결과 미성년자 케이스 — 본인 KYC SUCCESS 시점에 호출.
     * - ADULT: 가입 완료 (모든 필드 채움)
     * - MINOR: user는 생성되지만 guardian_verified_at=null → 보호자 인증 흐름 필요
     */
    fun createFromKyc(
        name: String,
        birthDate: LocalDate,
        gender: Gender,
        phone: String,
        ageGroup: AgeGroup,
    ): User {
        val newUser =
            User(
                publicCode = tokenGenerator.generatePublicCode(),
                name = name,
                birthDate = birthDate.toString(),
                gender = gender,
                phone = phone,
                ageGroup = ageGroup,
            )
        userRepository.save(newUser)
        val userId = checkNotNull(newUser.id) { "User id should be assigned after save" }

        auditLogger.log(
            eventType = AuditEvent.USER_CREATED,
            actorUserId = userId,
            entityType = ENTITY_USER,
            entityId = userId,
            metadata =
                mapOf(
                    "source" to "KYC",
                    "ageGroup" to ageGroup.name,
                ),
        )
        return newUser
    }

    /**
     * PASS 본인확인 결과로 신규 user 생성.
     *
     * - 호출 시점: PASS return endpoint 에서 ci_hash 매칭 ACTIVE user 없을 때
     * - ageGroup: birthday 로 자동 결정 (호출자가 판정 후 전달)
     *   - ADULT: 가입 완료
     *   - MINOR: user 생성되지만 guardian_verified_at=null → 보호자 PASS 흐름 (PASS-6)
     */
    fun createFromPass(
        ciHash: String,
        name: String,
        birthDate: LocalDate,
        gender: Gender,
        phone: String,
        ageGroup: AgeGroup,
    ): User {
        val newUser =
            User(
                publicCode = tokenGenerator.generatePublicCode(),
                name = name,
                birthDate = birthDate.toString(),
                gender = gender,
                phone = phone,
                ageGroup = ageGroup,
                ciHash = ciHash,
            )
        userRepository.save(newUser)
        val userId = checkNotNull(newUser.id) { "User id should be assigned after save" }

        auditLogger.log(
            eventType = AuditEvent.USER_CREATED,
            actorUserId = userId,
            entityType = ENTITY_USER,
            entityId = userId,
            metadata =
                mapOf(
                    "source" to "PASS",
                    "ageGroup" to ageGroup.name,
                ),
        )
        return newUser
    }

    @Transactional(readOnly = true)
    fun getByPublicCode(publicCode: String): User =
        userRepository.findByPublicCode(publicCode)
            ?: throw UserException.NotFound(publicCode)

    @Transactional(readOnly = true)
    fun getById(userId: Long): User =
        userRepository.findById(userId).orElseThrow {
            UserException.NotFound(userId.toString())
        }

    /**
     * 회원 탈퇴 — soft delete.
     *
     * - status=WITHDRAWN + withdrawnAt 설정 (User.withdraw() 위임)
     * - 연관 데이터 (identity_verifications, user_consents, guardian_links)는 보존 (audit + 법적)
     * - 재가입은 새 user 생성 (KycSessionService는 ACTIVE user의 SUCCESS만 차단, social 로그인은 WITHDRAWN 시 신규 user 발급)
     */
    fun withdraw(userId: Long) {
        val user = getById(userId)
        if (user.status == UserStatus.WITHDRAWN) {
            throw UserException.AlreadyWithdrawn(userId)
        }
        val isFraudConfirmed = user.fraudReportReceivedCount > 0
        user.withdraw()
        if (isFraudConfirmed) {
            fraudUserHashService.handleWithdrawal(user)
            user.maskFraudPii()
        }
        auditLogger.log(
            eventType = AuditEvent.USER_WITHDRAWN,
            actorUserId = userId,
            entityType = ENTITY_USER,
            entityId = userId,
        )
    }

    /** 마이페이지 푸시 토글 — Entity 메서드 위임 (dirty checking 으로 자동 UPDATE). */
    fun changePushEnabled(
        userId: Long,
        enabled: Boolean,
    ): User {
        val user = getById(userId)
        user.changePushEnabled(enabled)
        return user
    }
}

private const val ENTITY_USER = "USER"
