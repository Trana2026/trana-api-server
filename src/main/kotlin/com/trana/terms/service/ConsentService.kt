package com.trana.terms.service

import com.trana.audit.AuditEvent
import com.trana.audit.AuditLogger
import com.trana.guardian.service.GuardianLinkService
import com.trana.terms.dto.MyConsentResponse
import com.trana.terms.entity.ConsentContextType
import com.trana.terms.entity.UserConsent
import com.trana.terms.repository.TermsVersionRepository
import com.trana.terms.repository.UserConsentRepository
import com.trana.user.entity.AgeGroup
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 약관 동의 도메인 서비스.
 *
 * - 성인 가입 (userId=null): 서버가 signupSessionId UUID 발급 → Compare SUCCESS 시 backfillUserId
 * - 인증된 사용자 (userId 있음): 즉시 INSERT (signupSessionId=null)
 * - 보호자 약관 동의는 Phase 6에서 추가 (guardian_link_token 매칭)
 */
@Service
@Transactional
class ConsentService(
    private val userConsentRepository: UserConsentRepository,
    private val termsVersionRepository: TermsVersionRepository,
    private val termsService: TermsService,
    private val guardianLinkService: GuardianLinkService,
    private val auditLogger: AuditLogger,
) {
    /** 여러 약관에 한 번에 동의 — batch INSERT. 보호자 흐름은 idempotent (refactor ee). */
    @Suppress("LongMethod")
    fun agree(command: AgreeCommand): List<UserConsent> {
        require(command.termsVersionIds.isNotEmpty()) { "동의할 약관이 없습니다" }
        require(!(command.signupSessionId != null && command.guardianLinkToken != null)) {
            "signupSessionId 와 guardianLinkToken 은 동시에 사용할 수 없습니다"
        }
        if (command.guardianLinkToken != null) {
            guardianLinkService.findActive(command.guardianLinkToken) // 만료/사용된 token → GuardianException
        }
        require(command.ageGroup != AgeGroup.MINOR) {
            "미성년자 본인 동의는 지원하지 않습니다. 보호자 동의 흐름을 사용하세요"
        }
        command.termsVersionIds.forEach { termsService.getById(it) }

        val signupSessionId = resolveSignupSessionId(command)

        // 보호자 흐름 idempotency (refactor ee): 같은 (token, termsVersionId) 조합은 1회만
        val existingByToken: Map<Long, UserConsent> =
            if (command.guardianLinkToken != null) {
                userConsentRepository
                    .findAllByGuardianLinkToken(command.guardianLinkToken)
                    .associateBy { it.termsVersionId }
            } else {
                emptyMap()
            }

        val newConsents =
            command.termsVersionIds
                .filter { it !in existingByToken }
                .map { termsVersionId ->
                    val consent =
                        UserConsent(
                            termsVersionId = termsVersionId,
                            contextType = command.contextType,
                            ageGroup = command.ageGroup,
                            ip = command.ip,
                            contextId = command.contextId,
                            signupSessionId = signupSessionId,
                            guardianLinkToken = command.guardianLinkToken,
                            userAgent = command.userAgent,
                        )
                    if (command.userId != null) consent.assignUserId(command.userId)
                    consent
                }

        val saved = if (newConsents.isNotEmpty()) userConsentRepository.saveAll(newConsents) else emptyList()

        auditLogger.log(
            eventType = AuditEvent.CONSENT_AGREED,
            actorUserId = command.userId,
            entityType = "USER_CONSENT",
            metadata =
                mapOf(
                    "contextType" to command.contextType.name,
                    "ageGroup" to command.ageGroup.name,
                    "termsVersionIds" to command.termsVersionIds,
                    "newlyInsertedCount" to newConsents.size,
                    "totalReturnedCount" to command.termsVersionIds.size,
                    "signupSessionId" to signupSessionId?.toString(),
                    "guardianLinkTokenPrefix" to command.guardianLinkToken?.take(8),
                ),
            ip = command.ip,
        )

        // 요청 순서대로 (기존 + 신규) 합쳐 반환 — caller 응답 일관성
        return command.termsVersionIds.map { termsVersionId ->
            existingByToken[termsVersionId]
                ?: saved.first { it.termsVersionId == termsVersionId }
        }
    }

    /** 가입 세션 TTL 검증용 — IdentityService에서 호출. */
    @Transactional(readOnly = true)
    fun findFirstBySignupSessionId(signupSessionId: UUID): UserConsent? =
        userConsentRepository.findFirstBySignupSessionIdOrderByAgreedAtAsc(signupSessionId)

    /**
     * 성인 가입 완료(Compare SUCCESS) 시 — signup_session_id 매칭 row 중 user_id null 인 것만 백필 (refactor ff).
     *
     * idempotent — 이미 백필된 row 가 섞여있어도 throw 안 함 (운영 사고 재시도 / 부분 실패 후 재진입 안전).
     * 반환은 실제 백필 발생한 row 수 (skip 된 row 는 미포함).
     */
    fun backfillUserId(
        signupSessionId: UUID,
        userId: Long,
    ): Int {
        val toBackfill =
            userConsentRepository
                .findAllBySignupSessionId(signupSessionId)
                .filter { it.userId == null }
        toBackfill.forEach { it.assignUserId(userId) }
        return toBackfill.size
    }

    /** 마이페이지 — 본인의 가입 약관 동의 내역 (최신순). 미성년자는 빈 배열 (본인 동의 X, 보호자가 GUARDIAN_CONSENT 로 동의). */
    @Transactional(readOnly = true)
    fun findMyConsents(userId: Long): List<MyConsentResponse> {
        val consents =
            userConsentRepository.findAllByUserIdAndContextTypeOrderByAgreedAtDesc(
                userId = userId,
                contextType = ConsentContextType.SIGNUP,
            )
        if (consents.isEmpty()) return emptyList()

        val termsIds = consents.map { it.termsVersionId }.distinct()
        val termsById = termsVersionRepository.findAllById(termsIds).associateBy { it.id }

        return consents.map { consent ->
            val terms =
                termsById[consent.termsVersionId]
                    ?: error("Orphan user_consent: id=${consent.id}, termsVersionId=${consent.termsVersionId}")
            MyConsentResponse(
                termsId = consent.termsVersionId,
                type = terms.type,
                version = terms.version,
                title = terms.title,
                agreedAt = consent.agreedAt!!,
            )
        }
    }

    private fun resolveSignupSessionId(command: AgreeCommand): UUID? =
        when {
            command.userId != null -> null

            command.guardianLinkToken != null -> null

            // ← 보호자 흐름: 토큰이 키, signupSessionId 발급 X
            command.signupSessionId != null -> command.signupSessionId

            else -> UUID.randomUUID()
        }
}

data class AgreeCommand(
    val termsVersionIds: List<Long>,
    val contextType: ConsentContextType,
    val ageGroup: AgeGroup,
    val ip: String,
    val userId: Long? = null,
    val contextId: Long? = null,
    val signupSessionId: UUID? = null,
    val guardianLinkToken: String? = null,
    val userAgent: String? = null,
)
