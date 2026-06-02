package com.trana.common.dev

import com.trana.common.security.JwtProvider
import com.trana.user.repository.UserRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * 개발 전용 — 시드 user 의 JWT 발급 (local + Railway dev).
 *
 * 보안:
 * - @Profile("local", "dev") — prod profile 에서는 빈 자체 안 로드 → 404
 * - **X-Dev-Token-Key 헤더 필수** (refactor x — Railway dev 외부 노출 차단)
 *   yml `trana.dev.token-key` 또는 환경변수 `TRANA_DEV_TOKEN_KEY` 와 일치
 */
@Profile("local", "dev")
@RestController
@RequestMapping("/v1/dev")
@Tag(name = "Dev", description = "개발 전용 (local/dev profile)")
class DevTokenController(
    private val jwtProvider: JwtProvider,
    private val userRepository: UserRepository,
    private val devProperties: DevProperties,
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
}

data class DevTokenResponse(
    val userId: Long,
    val publicCode: String,
    val ageGroup: String?,
    val accessToken: String,
    val refreshToken: String,
)
