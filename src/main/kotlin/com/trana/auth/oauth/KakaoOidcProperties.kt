package com.trana.auth.oauth

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Kakao OIDC 검증 설정.
 *
 * - issuer: id_token 의 iss claim 검증 (https://kauth.kakao.com 고정)
 * - clientId: Web/JS SDK 흐름의 aud (REST API key)
 * - nativeAppKey: Android/iOS native SDK 흐름의 aud (native app key)
 *
 * Spring Security NimbusJwtDecoder가 issuer URL로 OIDC discovery →
 * jwks_uri 자동 발견 → public key 가져와서 RS256 서명 검증.
 *
 * audiences: REST API key + native app key 둘 다 허용 (multi-audience for cross-platform).
 */
@ConfigurationProperties(prefix = "trana.oauth.kakao")
data class KakaoOidcProperties(
    val issuer: String,
    val clientId: String,
    val nativeAppKey: String,
) {
    val audiences: List<String> get() = listOf(clientId, nativeAppKey)
}
