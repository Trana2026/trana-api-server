package com.trana.terms

import com.trana.user.AgeGroup
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class UserConsentService(
    private val userConsentRepository: UserConsentRepository,
    private val termsService: TermsService,
) {
    /**
     * 사용자가 여러 약관에 동의 — 한 호출로 N개 row 저장.
     * 약관 존재 검증 → batch INSERT.
     */
    fun agree(command: AgreeCommand): List<UserConsent> {
        require(command.termsVersionIds.isNotEmpty()) { "동의할 약관이 없습니다" }

        // 약관 존재 검증 (없는 ID면 TermsException.NotFound)
        command.termsVersionIds.forEach { termsService.getById(it) }

        val now = Instant.now()
        val consents =
            command.termsVersionIds.map { termsVersionId ->
                UserConsent(
                    userId = command.userId,
                    termsVersionId = termsVersionId,
                    contextType = command.contextType,
                    contextId = command.contextId,
                    signupSessionId = command.signupSessionId,
                    ageGroup = command.ageGroup,
                    agreedAt = now,
                    ip = command.ip,
                    userAgent = command.userAgent,
                )
            }

        return userConsentRepository.saveAll(consents)
    }
}

data class AgreeCommand(
    val userId: Long?,
    val termsVersionIds: List<Long>,
    val contextType: ConsentContextType,
    val ageGroup: AgeGroup,
    val ip: String,
    val userAgent: String? = null,
    val signupSessionId: UUID? = null,
    val contextId: Long? = null,
)
