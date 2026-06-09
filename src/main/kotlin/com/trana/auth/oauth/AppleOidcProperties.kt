package com.trana.auth.oauth

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Apple Sign In OIDC 검증 + OAuth client_secret 생성 설정.
 *
 * - issuer: id_token 의 iss claim (https://appleid.apple.com 고정)
 * - bundleId: iOS native flow 의 aud (App ID Bundle ID)
 * - clientId: Android/Web flow 의 aud + OAuth client_id (Services ID)
 * - teamId: Apple Developer Team ID — client_secret JWT 의 iss
 * - keyId: Sign in with Apple Key ID — client_secret JWT 의 kid 헤더
 * - privateKey: .p8 PEM body (BEGIN/END 포함) — client_secret ES256 서명 키
 *
 * audiences: iOS Bundle ID + Services ID 둘 다 허용 (multi-audience for cross-platform).
 */
@ConfigurationProperties(prefix = "trana.oauth.apple")
data class AppleOidcProperties(
    val issuer: String,
    val bundleId: String,
    val clientId: String,
    val teamId: String,
    val keyId: String,
    val privateKey: String,
    /** Flutter Android app package name — Android intent URL 의 package 파라미터. */
    val androidPackageName: String,
    /** Flutter sign_in_with_apple 패키지의 deeplink scheme (표준 "signinwithapple"). */
    val deeplinkScheme: String = "signinwithapple",
) {
    val audiences: List<String> get() = listOf(bundleId, clientId)
}
