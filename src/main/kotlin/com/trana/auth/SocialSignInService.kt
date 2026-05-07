package com.trana.auth

import com.trana.user.SocialProvider
import com.trana.user.UserService
import org.springframework.stereotype.Service

/**
 * 소셜 로그인 Use Case.
 *
 * 흐름:
 * 1. provider별 SocialAuthAdapter 선택
 * 2. 공급자 API로 사용자 정보 조회
 * 3. UserService로 가입/조회
 * 4. JWT (access + refresh) 발급
 *
 * 주의:
 * - 트랜잭션 경계는 UserService가 담당 (외부 API 호출은 트랜잭션 밖)
 * - SocialAuthAdapter는 List로 주입 → provider별 분기 자동
 */
@Service
class SocialSignInService(
    private val socialAuthAdapters: List<SocialAuthAdapter>,
    private val userService: UserService,
    private val jwtProvider: JwtProvider,
) {
    private val adaptersByProvider: Map<SocialProvider, SocialAuthAdapter> =
        socialAuthAdapters.associateBy { it.provider }

    fun signIn(request: SocialSignInRequest): SignInResponse {
        val adapter = adaptersByProvider[request.provider]
            ?: throw AuthException.UnsupportedProvider(request.provider)

        val socialUser = adapter.fetchUserInfo(request.accessToken)

        val user = userService.findOrCreateBySocial(
            provider = socialUser.provider,
            providerUserId = socialUser.providerUserId,
            email = socialUser.email,
            nickname = socialUser.nickname,
        )

        val userId = checkNotNull(user.id) { "User id should be assigned after save" }

        return SignInResponse(
            accessToken = jwtProvider.createAccessToken(userId),
            refreshToken = jwtProvider.createRefreshToken(userId),
            publicCode = user.publicCode,
        )
    }

    fun refresh(request: RefreshRequest): SignInResponse {
        val userId = try {
            jwtProvider.extractUserId(request.refreshToken)
        } catch (ex: io.jsonwebtoken.JwtException) {
            throw AuthException.InvalidToken("refresh token 검증 실패: ${ex.message}")
        }

        val user = userService.getById(userId)

        return SignInResponse(
            accessToken = jwtProvider.createAccessToken(userId),
            refreshToken = jwtProvider.createRefreshToken(userId),
            publicCode = user.publicCode,
        )
    }
}
