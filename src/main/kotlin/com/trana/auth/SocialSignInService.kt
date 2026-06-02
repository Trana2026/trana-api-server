package com.trana.auth

import com.trana.auth.oauth.SocialAuthAdapter
import com.trana.auth.oauth.SocialProvider
import com.trana.common.security.JwtProvider
import com.trana.user.entity.AgeGroup
import com.trana.user.service.UserService
import org.springframework.stereotype.Service

@Service
class SocialSignInService(
    private val socialAuthAdapters: List<SocialAuthAdapter>,
    private val userService: UserService,
    private val jwtProvider: JwtProvider,
) {
    private val adaptersByProvider: Map<SocialProvider, SocialAuthAdapter> =
        socialAuthAdapters.associateBy { it.provider }

    fun signIn(request: SocialSignInRequest): SignInResponse {
        require(request.ageGroup == AgeGroup.MINOR) {
            "소셜 로그인은 미성년자(MINOR)만 가능합니다. 성인은 본인 KYC 흐름으로 가입하세요."
        }

        val adapter =
            adaptersByProvider[request.provider]
                ?: throw AuthException.UnsupportedProvider(request.provider)

        val socialUser = adapter.verify(request.idToken)

        val user =
            userService.findOrCreateBySocial(
                provider = socialUser.provider,
                providerUserId = socialUser.providerUserId,
                email = socialUser.email,
                nickname = socialUser.nickname,
                ageGroup = request.ageGroup,
            )

        val userId = checkNotNull(user.id) { "User id should be assigned after save" }

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
}
