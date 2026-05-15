package com.trana.auth.oauth

import com.trana.user.SocialProvider

/**
 * 소셜 공급자별 OIDC id_token 검증 추상화.
 *
 * 클라이언트(Flutter SDK)가 받아온 id_token (OIDC JWT)을
 * 공급자별 JwtDecoder로 검증하고 사용자 정보 추출.
 *
 * 각 공급자별 구현:
 * - KakaoAuthAdapter: Kakao OIDC (kauth.kakao.com)
 * - GoogleAuthAdapter: Google OIDC (accounts.google.com)
 * - AppleAuthAdapter: Apple Sign In (appleid.apple.com) — W6 이후
 *
 * 검증 항목 (NimbusJwtDecoder가 처리):
 * - 서명 (JWKS public key, RS256)
 * - iss (issuer)
 * - exp (만료)
 * - aud (우리 client-id 일치)
 */
interface SocialAuthAdapter {
    val provider: SocialProvider

    /**
     * id_token 검증 후 사용자 정보 추출.
     *
     * @throws com.trana.auth.AuthException.InvalidSocialToken 검증 실패
     */
    fun verify(idToken: String): SocialUserInfo
}
