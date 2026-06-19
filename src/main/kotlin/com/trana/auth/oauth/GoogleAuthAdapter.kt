package com.trana.auth.oauth

import com.trana.auth.AuthException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.stereotype.Component

/**
 * Google OIDC id_token 검증 어댑터.
 *
 * Flutter SDK가 serverClientId(Web Client ID) + scopes=["openid","email","profile"]로
 * 로그인 → id_token에 sub, email, name, picture claims 포함.
 */
@Component
class GoogleAuthAdapter(
    @Qualifier("googleIdTokenDecoder")
    private val jwtDecoder: JwtDecoder,
) : SocialAuthAdapter {
    override val provider = SocialProvider.GOOGLE

    @Suppress("UNUSED_PARAMETER")
    override fun verify(
        idToken: String,
        nonce: String?,
    ): SocialUserInfo {
        val jwt =
            try {
                jwtDecoder.decode(idToken)
            } catch (ex: JwtException) {
                throw AuthException.InvalidSocialToken(SocialProvider.GOOGLE, cause = ex)
            }

        val sub =
            jwt.subject
                ?: throw AuthException.InvalidSocialToken(SocialProvider.GOOGLE)

        return SocialUserInfo(
            provider = SocialProvider.GOOGLE,
            providerUserId = sub,
            email = jwt.getClaimAsString("email"),
            name = jwt.getClaimAsString("name"),
        )
    }
}
