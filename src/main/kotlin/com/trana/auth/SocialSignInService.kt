package com.trana.auth

import com.trana.audit.AuditEvent
import com.trana.audit.AuditLogger
import com.trana.auth.oauth.SocialAuthAdapter
import com.trana.auth.oauth.SocialProvider
import com.trana.common.security.JwtProvider
import com.trana.notification.service.DeviceTokenService
import com.trana.user.entity.AgeGroup
import com.trana.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class SocialSignInService(
    private val socialAuthAdapters: List<SocialAuthAdapter>,
    private val userService: UserService,
    private val jwtProvider: JwtProvider,
    private val auditLogger: AuditLogger,
    private val deviceTokenService: DeviceTokenService,
) {
    private val adaptersByProvider: Map<SocialProvider, SocialAuthAdapter> =
        socialAuthAdapters.associateBy { it.provider }

    /**
     * 소셜 로그인 — OAuth provider verify (JWKS HTTP I/O) 가 DB 트랜잭션 안에 들어가지 않도록 명시 가드 (refactor u).
     *
     * - `adapter.verify(idToken)` 는 콜드 캐시 시 JWKS fetch 발생 → 트랜잭션 안에서 풀 점유 risk
     * - `findOrCreateBySocial` 의 자체 @Transactional 만 별도 짧은 트랜잭션
     * - NOT_SUPPORTED: caller 가 트랜잭션 열고 호출해도 suspend → verify 가 트랜잭션 밖 보장
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun signIn(request: SocialSignInRequest): SignInResponse {
        require(request.ageGroup == AgeGroup.MINOR) {
            "소셜 로그인은 미성년자(MINOR)만 가능합니다. 성인은 본인 KYC 흐름으로 가입하세요."
        }

        val adapter =
            adaptersByProvider[request.provider]
                ?: throw AuthException.UnsupportedProvider(request.provider)

        val socialUser = adapter.verify(request.idToken, request.nonce)

        val user =
            userService.findOrCreateBySocial(
                provider = socialUser.provider,
                providerUserId = socialUser.providerUserId,
                email = socialUser.email,
                name = socialUser.name,
                ageGroup = request.ageGroup,
            )

        val userId = checkNotNull(user.id) { "User id should be assigned after save" }

        auditLogger.log(
            eventType = AuditEvent.USER_SIGNED_IN,
            actorUserId = userId,
            entityType = "USER",
            entityId = userId,
            metadata =
                mapOf(
                    "provider" to socialUser.provider.name,
                    "ageGroup" to request.ageGroup.name,
                ),
        )

        return SignInResponse(
            accessToken = jwtProvider.createAccessToken(userId),
            refreshToken = jwtProvider.createRefreshToken(userId),
            publicCode = user.publicCode,
        )
    }

    fun refresh(request: RefreshRequest): SignInResponse {
        val userId =
            try {
                jwtProvider.extractUserIdFromRefreshToken(request.refreshToken)
            } catch (ex: io.jsonwebtoken.JwtException) {
                throw AuthException.InvalidToken("refresh token 검증 실패: ${ex.message}", cause = ex)
            }

        val user = userService.getById(userId)

        return SignInResponse(
            accessToken = jwtProvider.createAccessToken(userId),
            refreshToken = jwtProvider.createRefreshToken(userId),
            publicCode = user.publicCode,
        )
    }

    /**
     * 로그아웃 — audit 기록 + 선택적 device token 정리.
     *
     * - JWT 무효화는 X (stateless 정책, access 15분 자연 만료)
     * - deviceToken 제공 시 본인 token 매칭 row 삭제 (멱등 — 기존 DeviceTokenService.unregister 재활용)
     * - audit 는 항상 기록 (IP/UA 는 RequestMdcFilter 가 MDC 자동 채움)
     */
    fun logout(
        userId: Long,
        deviceToken: String?,
    ) {
        auditLogger.log(
            eventType = AuditEvent.USER_SIGNED_OUT,
            actorUserId = userId,
            entityType = "USER",
            entityId = userId,
        )
        if (deviceToken != null) {
            deviceTokenService.unregister(userId, deviceToken)
        }
    }
}
