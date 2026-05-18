package com.trana.auth.oauth

import com.trana.auth.AuthException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.stereotype.Component

/**
 * Kakao OIDC id_token 검증 어댑터.
 *
 * Kakao Developers console에서 OpenID Connect 활성화 필수.
 * Flutter SDK가 scope에 "openid" 요청 → 토큰 응답에 id_token 포함.
 */
@Component
class KakaoAuthAdapter(
    @Qualifier("kakaoIdTokenDecoder")
    private val jwtDecoder: JwtDecoder,
) : SocialAuthAdapter {
    override val provider = SocialProvider.KAKAO

    override fun verify(idToken: String): SocialUserInfo {
        val jwt =
            try {
                jwtDecoder.decode(idToken)
            } catch (ex: JwtException) {
                throw AuthException.InvalidSocialToken(SocialProvider.KAKAO, cause = ex)
            }

        val sub =
            jwt.subject
                ?: throw AuthException.InvalidSocialToken(SocialProvider.KAKAO)

        return SocialUserInfo(
            provider = SocialProvider.KAKAO,
            providerUserId = sub,
            email = jwt.getClaimAsString("email"),
            nickname = jwt.getClaimAsString("nickname"),
        )
    }
}
