package com.trana.common.demo

import com.trana.common.security.JwtProvider
import com.trana.user.repository.UserRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * 데모 로그인 — 심사용. 운영(prod) 포함 전 프로파일에서 동작하되 이중 잠금.
 *
 * 보안:
 * - trana.demo.enabled=false(기본) 면 404 — 존재 자체를 숨김(승인 후 TRANA_DEMO_ENABLED 로 차단).
 * - X-Dev-Token-Key 헤더가 trana.demo.token-key 와 일치해야 함(빈 값이면 항상 거부).
 * - 발급 JWT 는 이 서버의 DB 계정 기준 — 운영 DB 에 해당 계정(shareCode)이 있어야 함.
 *
 * 기존 dev endpoint(/v1/dev) 는 @Profile("!prod") 로 운영에 없음. 심사에 필요한 로그인만 이 endpoint 로 연다.
 */
@RestController
@RequestMapping("/v1/demo")
@Tag(name = "Demo", description = "데모 모드 (심사 대응)")
class DemoLoginController(
    private val demoProperties: DemoProperties,
    private val jwtProvider: JwtProvider,
    private val userRepository: UserRepository,
) {
    @Operation(
        summary = "데모 로그인 (심사용)",
        description =
            "demo 모드 활성 + X-Dev-Token-Key 일치 시 shareCode(우선) 또는 publicCode 로 JWT 발급. " +
                "demo 비활성이면 404. 운영 DB 에 해당 계정이 있어야 함.",
    )
    @GetMapping("/login")
    @Suppress("ThrowsCount")
    fun login(
        @RequestHeader(value = "X-Dev-Token-Key", required = false) providedKey: String?,
        @RequestParam(required = false) shareCode: String?,
        @RequestParam(required = false) publicCode: String?,
    ): DemoLoginResponse {
        if (!demoProperties.enabled) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND) // 비활성 — 존재 숨김
        }
        if (demoProperties.tokenKey.isBlank() || providedKey != demoProperties.tokenKey) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "X-Dev-Token-Key 검증 실패")
        }
        val user =
            when {
                !shareCode.isNullOrBlank() -> userRepository.findByShareCode(shareCode.uppercase())
                !publicCode.isNullOrBlank() -> userRepository.findByPublicCode(publicCode)
                else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "shareCode 또는 publicCode 필요")
            } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "계정을 찾을 수 없습니다")
        val userId = requireNotNull(user.id)
        return DemoLoginResponse(
            userId = userId,
            publicCode = user.publicCode,
            ageGroup = user.ageGroup?.name,
            accessToken = jwtProvider.createAccessToken(userId),
            refreshToken = jwtProvider.createRefreshToken(userId),
        )
    }
}

data class DemoLoginResponse(
    val userId: Long,
    val publicCode: String,
    val ageGroup: String?,
    val accessToken: String,
    val refreshToken: String,
)
