package com.trana.guardian

import com.trana.audit.AuditLogger
import com.trana.user.AgeGroup
import com.trana.user.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 보호자 매칭 링크 도메인 서비스.
 *
 * - 발급: 미성년자(MINOR) + 미인증(guardian_verified_at=null) 사용자만
 * - 재발급: 기존 활성 토큰은 revoke 처리 → 새 토큰 1개만 유효
 * - 조회: 토큰 유효성(미사용 + 미취소 + 미만료) 검증, 실패 시 도메인 예외
 */
@Service
class GuardianLinkService(
    private val repository: GuardianLinkRepository,
    private val userService: UserService,
    private val tokenGenerator: GuardianTokenGenerator,
    private val auditLogger: AuditLogger,
) {
    @Transactional
    fun createLink(minorUserId: Long): GuardianLink {
        val user = userService.getById(minorUserId)
        if (user.ageGroup != AgeGroup.MINOR) throw GuardianException.NotMinor(minorUserId)
        if (user.guardianVerifiedAt != null) throw GuardianException.AlreadyVerified(minorUserId)

        repository
            .findAllByMinorUserIdAndUsedAtIsNullAndRevokedAtIsNull(minorUserId)
            .forEach { it.revoke() }

        val now = Instant.now()
        val link =
            repository.save(
                GuardianLink(
                    token = tokenGenerator.generate(),
                    minorUserId = minorUserId,
                    expiresAt = now.plus(TTL_DAYS, ChronoUnit.DAYS),
                ),
            )

        auditLogger.log(
            eventType = EVENT_LINK_CREATED,
            actorUserId = minorUserId,
            entityType = ENTITY_GUARDIAN_LINK,
            entityId = link.id,
            metadata =
                mapOf(
                    "token" to link.token,
                    "expiresAt" to link.expiresAt.toString(),
                ),
        )

        return link
    }

    @Transactional(readOnly = true)
    fun findValidLink(token: String): GuardianLink {
        val link = repository.findByToken(token) ?: throw GuardianException.LinkNotFound(token)
        if (!link.isUsable()) throw GuardianException.LinkInvalid(token)
        return link
    }
}

private const val TTL_DAYS = 3L
private const val EVENT_LINK_CREATED = "GUARDIAN_LINK_CREATED"
private const val ENTITY_GUARDIAN_LINK = "GUARDIAN_LINK"
