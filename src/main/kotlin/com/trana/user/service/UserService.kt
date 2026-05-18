package com.trana.user.service

import com.trana.audit.AuditLogger
import com.trana.auth.oauth.SocialProvider
import com.trana.common.util.PublicCodeGenerator
import com.trana.user.UserException
import com.trana.user.entity.AgeGroup
import com.trana.user.entity.Gender
import com.trana.user.entity.SocialAccount
import com.trana.user.entity.User
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
    private val publicCodeGenerator: PublicCodeGenerator,
    private val auditLogger: AuditLogger,
) {
    /**
     * 소셜 로그인: 기존 매핑이 있으면 기존 사용자 반환, 없으면 신규 생성.
     * 미성년자 가입 전용 흐름 — 호출 시 ageGroup=MINOR 명시.
     */
    fun findOrCreateBySocial(
        provider: SocialProvider,
        providerUserId: String,
        email: String? = null,
        nickname: String? = null,
        ageGroup: AgeGroup,
    ): User {
        val existing = socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)
        if (existing != null) {
            return userRepository.findById(existing.userId).orElseThrow {
                IllegalStateException(
                    "Orphan social_account: id=${existing.id}, userId=${existing.userId}",
                )
            }
        }

        val newUser =
            User(
                publicCode = publicCodeGenerator.generate(),
                email = email,
                nickname = nickname,
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
            eventType = EVENT_USER_CREATED,
            actorUserId = userId,
            entityType = ENTITY_USER,
            entityId = userId,
            metadata =
                mapOf(
                    "source" to "SOCIAL",
                    "provider" to provider.name,
                    "ageGroup" to ageGroup.name,
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
                publicCode = publicCodeGenerator.generate(),
                name = name,
                birthDate = birthDate.toString(),
                gender = gender,
                phone = phone,
                ageGroup = ageGroup,
            )
        userRepository.save(newUser)
        val userId = checkNotNull(newUser.id) { "User id should be assigned after save" }

        auditLogger.log(
            eventType = EVENT_USER_CREATED,
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

    @Transactional(readOnly = true)
    fun getByPublicCode(publicCode: String): User =
        userRepository.findByPublicCode(publicCode)
            ?: throw UserException.NotFound(publicCode)

    @Transactional(readOnly = true)
    fun getById(userId: Long): User =
        userRepository.findById(userId).orElseThrow {
            UserException.NotFound(userId.toString())
        }
}

private const val EVENT_USER_CREATED = "USER_CREATED"
private const val ENTITY_USER = "USER"
