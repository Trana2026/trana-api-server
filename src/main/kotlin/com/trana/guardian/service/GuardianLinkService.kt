package com.trana.guardian.service

import com.trana.audit.AuditEvent
import com.trana.audit.AuditLogger
import com.trana.common.util.TokenGenerator
import com.trana.guardian.GuardianException
import com.trana.guardian.entity.GuardianLink
import com.trana.guardian.repository.GuardianLinkRepository
import com.trana.user.entity.AgeGroup
import com.trana.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * 보호자 링크 발급/조회.
 *
 * - create(userId): 미성년자가 보호자에게 보낼 일회용 토큰 발급
 * - findActive(token): Phase 6 보호자 KYC에서 토큰 검증용
 * - markUsed(token): Phase 6 Compare SUCCESS 시 호출
 *
 * 보호자 KYC 자체는 identity 도메인 책임 (Phase 6).
 */
@Service
@Transactional
class GuardianLinkService(
    private val guardianLinkRepository: GuardianLinkRepository,
    private val userService: UserService,
    private val tokenGenerator: TokenGenerator,
    private val auditLogger: AuditLogger,
) {
    fun create(userId: Long): GuardianLink {
        val user = userService.getById(userId)
        if (user.ageGroup != AgeGroup.MINOR) {
            throw GuardianException.NotMinor(userId)
        }
        if (user.guardianVerifiedAt != null) {
            throw GuardianException.AlreadyVerified(userId)
        }

        val link =
            GuardianLink(
                token = tokenGenerator.generateGuardianLink(),
                userId = userId,
                expiresAt = Instant.now().plus(TTL),
            )
        val saved = guardianLinkRepository.save(link)

        auditLogger.log(
            eventType = AuditEvent.GUARDIAN_LINK_CREATED,
            actorUserId = userId,
            entityType = "GUARDIAN_LINK",
            metadata = mapOf("tokenPrefix" to saved.token.take(8)),
        )
        return saved
    }

    /**
     * 활성 link 조회 (Phase 6 보호자 KYC 진입점에서 호출).
     * @throws GuardianException.LinkNotFound / LinkInvalid
     */
    @Transactional(readOnly = true)
    fun findActive(token: String): GuardianLink {
        val link =
            guardianLinkRepository.findById(token).orElseThrow {
                GuardianException.LinkNotFound(token)
            }
        return link.also { validateActive(it) }
    }

    /**
     * 1회용 토큰 사용 처리 — conditional UPDATE atomic (refactor cc).
     *
     * - `WHERE used_at IS NULL AND expires_at > now` 매칭 시만 affected=1
     * - 동시 진입 race 시 한쪽만 통과, 다른쪽 LinkInvalid → 트랜잭션 rollback
     * - affected=0 시 findById 로 사유 분류 (이미 사용 / 만료 / 없음)
     */
    fun markUsed(token: String) {
        val now = Instant.now()
        val affected = guardianLinkRepository.markUsedAtomically(token, now)
        if (affected == 1) return

        val link =
            guardianLinkRepository.findById(token).orElseThrow {
                GuardianException.LinkNotFound(token)
            }
        val reason =
            when {
                link.usedAt != null -> "이미 사용된 토큰"
                link.isExpired(now) -> "만료된 토큰"
                else -> "토큰 상태 확인 실패"
            }
        throw GuardianException.LinkInvalid(token, reason)
    }

    private fun validateActive(link: GuardianLink) {
        val now = Instant.now()
        if (link.usedAt != null) throw GuardianException.LinkInvalid(link.token, "이미 사용된 토큰")
        if (link.isExpired(now)) throw GuardianException.LinkInvalid(link.token, "만료된 토큰")
    }

    companion object {
        private val TTL: Duration = Duration.ofDays(3)
    }
}
