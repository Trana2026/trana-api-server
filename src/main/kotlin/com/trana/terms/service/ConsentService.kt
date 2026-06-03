package com.trana.terms.service

import com.trana.guardian.service.GuardianLinkService
import com.trana.terms.entity.ConsentContextType
import com.trana.terms.entity.UserConsent
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
    private val termsService: TermsService,
    private val guardianLinkService: GuardianLinkService,
) {
    /** 여러 약관에 한 번에 동의 — batch INSERT. 보호자 흐름은 idempotent (refactor ee). */
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

    /** 성인 가입 완료(Compare SUCCESS) 시 — signup_session_id 매칭 row 모두에 user_id 백필. */
    fun backfillUserId(
        signupSessionId: UUID,
        userId: Long,
    ): Int {
        val consents = userConsentRepository.findAllBySignupSessionId(signupSessionId)
        consents.forEach { it.assignUserId(userId) }
        return consents.size
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
