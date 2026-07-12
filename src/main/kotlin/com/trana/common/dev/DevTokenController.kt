package com.trana.common.dev

import com.trana.common.security.JwtProvider
import com.trana.guardian.repository.GuardianLinkRepository
import com.trana.identity.repository.IdentityVerificationRepository
import com.trana.user.repository.UserRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

/**
 * 개발 전용 — 시드 user 의 JWT 발급 + 보호자 검증 리셋 (local + Railway dev).
 *
 * 보안:
 * - @Profile("local", "dev") — prod profile 에서는 빈 자체 안 로드 → 404
 * - **X-Dev-Token-Key 헤더 필수** — yml `trana.dev.token-key` 또는 환경변수 `TRANA_DEV_TOKEN_KEY` 와 일치
 */
@Profile("!prod")
@RestController
@RequestMapping("/v1/dev")
@Tag(name = "Dev", description = "개발 전용 (local/dev profile)")
class DevTokenController(
    private val jwtProvider: JwtProvider,
    private val userRepository: UserRepository,
    private val devProperties: DevProperties,
    private val guardianLinkRepository: GuardianLinkRepository,
    private val identityVerificationRepository: IdentityVerificationRepository,
) {
    @Operation(
        summary = "publicCode 로 JWT 발급 (개발용)",
        description =
            "prod profile 에서는 endpoint 가 존재하지 않습니다 (404). " +
                "X-Dev-Token-Key 헤더 (yml `trana.dev.token-key` 와 일치) 필수 — 불일치 시 403.",
    )
    @GetMapping("/token")
    fun token(
        @RequestHeader(value = "X-Dev-Token-Key", required = false) providedKey: String?,
        @RequestParam publicCode: String,
    ): DevTokenResponse {
        if (providedKey != devProperties.tokenKey) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "X-Dev-Token-Key 헤더 검증 실패")
        }
        val user =
            userRepository.findByPublicCode(publicCode)
                ?: throw IllegalArgumentException("seeded user not found: $publicCode")
        val userId = requireNotNull(user.id)
        return DevTokenResponse(
            userId = userId,
            publicCode = user.publicCode,
            ageGroup = user.ageGroup?.name,
            accessToken = jwtProvider.createAccessToken(userId),
            refreshToken = jwtProvider.createRefreshToken(userId),
        )
    }

    @Operation(
        summary = "미성년 보호자 검증 리셋 (개발용)",
        description =
            "미성년 user 의 guardian_verified_at 을 null 로 리셋 + 해당 user 의 guardian_links + " +
                "GUARDIAN identity_verifications 전체 삭제. 이후 새 링크 발급 → 실 PASS 흐름 반복 테스트 가능. " +
                "X-Dev-Token-Key 헤더 필수 — 불일치 시 403. 성인 대상 호출 시 IllegalStateException.",
    )
    @PostMapping("/guardian/reset")
    @Transactional
    fun resetGuardianVerification(
        @RequestHeader(value = "X-Dev-Token-Key", required = false) providedKey: String?,
        @RequestParam publicCode: String,
    ): DevGuardianResetResponse {
        if (providedKey != devProperties.tokenKey) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "X-Dev-Token-Key 헤더 검증 실패")
        }
        val user =
            userRepository.findByPublicCode(publicCode)
                ?: throw IllegalArgumentException("seeded user not found: $publicCode")
        val userId = requireNotNull(user.id)

        val deletedLinks = guardianLinkRepository.deleteAllByUserId(userId)
        val deletedVerifications = identityVerificationRepository.deleteAllBySubjectUserId(userId)
        user.resetGuardianVerification()
        userRepository.save(user)

        return DevGuardianResetResponse(
            publicCode = user.publicCode,
            guardianVerifiedAt = user.guardianVerifiedAt,
            deletedLinks = deletedLinks,
            deletedVerifications = deletedVerifications,
        )
    }
}

data class DevTokenResponse(
    val userId: Long,
    val publicCode: String,
    val ageGroup: String?,
    val accessToken: String,
    val refreshToken: String,
)

data class DevGuardianResetResponse(
    val publicCode: String,
    val guardianVerifiedAt: Instant?,
    val deletedLinks: Int,
    val deletedVerifications: Int,
)
