package com.trana.common.dev

import com.trana.common.demo.DemoProperties
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
 * 운영(prod) 전용 데모 로그인 — 심사 대응. 경로는 dev 와 동일(/v1/dev/token) 하여 프론트 무변경.
 *
 * - dev/local: DevTokenController(@Profile("!prod")) 가 담당 — 이 클래스는 prod 전용이라 충돌 없음.
 * - prod: 로그인만 노출(guardian reset 등 위험 dev 메서드는 운영에 열지 않음).
 * - 이중 잠금: trana.demo.enabled=false(기본) 면 404, X-Dev-Token-Key 불일치 시 403.
 *   승인 후 TRANA_DEMO_ENABLED=false 로 즉시 차단.
 */
@Profile("prod")
@RestController
@RequestMapping("/v1/dev")
@Tag(name = "Dev", description = "운영 데모 로그인 (심사 대응)")
class ProdDevTokenController(
    private val demoProperties: DemoProperties,
    private val jwtProvider: JwtProvider,
    private val userRepository: UserRepository,
) {
    @Operation(
        summary = "데모 로그인 (운영 심사용)",
        description =
            "demo 모드 활성 + X-Dev-Token-Key 일치 시 publicCode(우선) 또는 shareCode 로 JWT 발급. " +
                "demo 비활성이면 404. 운영 DB 에 해당 계정이 있어야 함.",
    )
    @GetMapping("/token")
    @Suppress("ThrowsCount")
    fun token(
        @RequestHeader(value = "X-Dev-Token-Key", required = false) providedKey: String?,
        @RequestParam(required = false) publicCode: String?,
        @RequestParam(required = false) shareCode: String?,
    ): DevTokenResponse {
        if (!demoProperties.enabled) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND) // 비활성 — 존재 숨김
        }
        if (demoProperties.tokenKey.isBlank() || providedKey != demoProperties.tokenKey) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "X-Dev-Token-Key 검증 실패")
        }
        val user =
            when {
                !publicCode.isNullOrBlank() -> userRepository.findByPublicCode(publicCode)
                !shareCode.isNullOrBlank() -> userRepository.findByShareCode(shareCode.uppercase())
                else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "publicCode 또는 shareCode 필요")
            } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "계정을 찾을 수 없습니다")
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
