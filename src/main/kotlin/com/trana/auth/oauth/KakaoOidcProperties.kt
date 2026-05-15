package com.trana.auth.oauth

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Kakao OIDC 검증 설정.
 *
 * - issuer: id_token의 iss claim 검증 (https://kauth.kakao.com 고정)
 * - clientId: id_token의 aud claim 검증 (REST API 키)
 *
 * Spring Security NimbusJwtDecoder가 issuer URL로 OIDC discovery →
 * jwks_uri 자동 발견 → public key 가져와서 RS256 서명 검증.
 */
@ConfigurationProperties(prefix = "trana.oauth.kakao")
data class KakaoOidcProperties(
    val issuer: String,
    val clientId: String,
)
