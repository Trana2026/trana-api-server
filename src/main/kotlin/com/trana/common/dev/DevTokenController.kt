package com.trana.common.dev

import com.trana.common.security.JwtProvider
import com.trana.user.repository.UserRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 *
 * 개발 전용 — 시드 user 의 JWT 발급 (local + Railway dev).
 *
 * 보안:
 * - @Profile("local", "dev") — prod profile 에서는 빈 자체 안 로드 → 404
 * - SecurityConfig 화이트리스트에 /v1/dev/`**` 추가 (prod 에서도 화이트리스트지만 controller 없어서 404)
 *
 * 용도:
 * - 프론트 (Flutter / Next.js) 가 publicCode 만 알면 토큰 발급 가능
 * - 시드 4명 + 신규 가입자 모두 지원 (publicCode 만 있으면 됨)
 */

@Profile("local", "dev")
@RestController
@RequestMapping("/v1/dev")
@Tag(name = "Dev", description = "개발 전용 (local/dev profile)")
class DevTokenController(
    private val jwtProvider: JwtProvider,
    private val userRepository: UserRepository,
) {
    @Operation(
        summary = "publicCode 로 JWT 발급 (개발용)",
        description = "prod profile 에서는 endpoint 가 존재하지 않습니다 (404).",
    )
    @GetMapping("/token")
    fun token(
        @RequestParam publicCode: String,
    ): DevTokenResponse {
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
