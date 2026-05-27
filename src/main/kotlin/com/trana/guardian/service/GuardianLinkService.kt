package com.trana.guardian.service

import com.trana.audit.AuditLogger
import com.trana.common.util.GuardianLinkTokenGenerator
import com.trana.guardian.GuardianException
import com.trana.guardian.entity.GuardianLink
import com.trana.guardian.entity.LinkPurpose
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
    private val tokenGenerator: GuardianLinkTokenGenerator,
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
                token = tokenGenerator.generate(),
                userId = userId,
                expiresAt = Instant.now().plus(TTL),
            )
        val saved = guardianLinkRepository.save(link)

        auditLogger.log(
            eventType = "GUARDIAN_LINK_CREATED",
            actorUserId = userId,
            entityType = "GUARDIAN_LINK",
            metadata = mapOf("tokenPrefix" to saved.token.take(8)),
        )
        return saved
    }

    /**
     * 계약 보호자 동의용 링크 발급 (W4+, purpose=CONTRACT_CONSENT).
     *
     * - 미성년자가 본인 작성 계약마다 1회 발급 (재발급 가능)
     * - 보호자가 토큰으로 KYC 진행 → Compare SUCCESS 시 contract.markGuardianConsented() + link.markUsed()
     * - SIGNUP 용 create() 와 달리 guardianVerifiedAt 검사 안 함 (가입 완료된 미성년 전제)
     */
    fun createForContract(
        minorUserId: Long,
        contractId: Long,
    ): GuardianLink {
        val user = userService.getById(minorUserId)
        if (user.ageGroup != AgeGroup.MINOR) {
            throw GuardianException.NotMinor(minorUserId)
        }

        val link =
            GuardianLink(
                token = tokenGenerator.generate(),
                userId = minorUserId,
                expiresAt = Instant.now().plus(TTL),
                purpose = LinkPurpose.CONTRACT_CONSENT,
                contractId = contractId,
            )
        val saved = guardianLinkRepository.save(link)

        auditLogger.log(
            eventType = "CONTRACT_GUARDIAN_LINK_CREATED",
            actorUserId = minorUserId,
            entityType = "GUARDIAN_LINK",
            metadata =
                mapOf(
                    "tokenPrefix" to saved.token.take(8),
                    "contractId" to contractId.toString(),
                ),
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

    /** Phase 6 Compare SUCCESS 시 호출 — 재사용 차단. */
    fun markUsed(token: String) {
        val link = findActive(token)
        link.markUsed()
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
