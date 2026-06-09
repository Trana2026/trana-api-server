package com.trana.auth

import com.trana.auth.oauth.AppleOidcProperties
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Apple Sign In OAuth callback (Android web flow).
 *
 * Apple Authorization Server 가 form_post 로 호출. id_token 검증 안 함 — 단순 protocol bridge.
 * 받은 fields 를 Flutter Android intent URL 의 query string 으로 변환 → 302 redirect.
 *
 * Flutter `sign_in_with_apple` 패키지가 intent URL deeplink 를 intercept → id_token 추출 →
 * 기존 /v1/auth/social/sign-in (provider=APPLE) 호출로 검증/로그인.
 *
 * SecurityConfig 의 `/v1/auth/**/` 화이트리스트로 인증 우회.
 * CSRF 는 stateless 정책으로 disable 상태 (Apple 가 form_post 보내는 흐름이라 적합).
 */
@RestController
@RequestMapping("/v1/auth/apple")
class AppleAuthController(
    private val appleProps: AppleOidcProperties,
    private val revokeService: AppleRevokeService,
) : AppleAuthApi {
    override fun callback(
        code: String?,
        state: String?,
        idToken: String?,
        user: String?,
    ): ResponseEntity<Void> {
        val params =
            buildList {
                code?.let { add("code" to it) }
                state?.let { add("state" to it) }
                idToken?.let { add("id_token" to it) }
                user?.let { add("user" to it) }
            }
        val query = params.joinToString("&") { (k, v) -> "${urlEncode(k)}=${urlEncode(v)}" }
        val packageName = appleProps.androidPackageName
        val scheme = appleProps.deeplinkScheme
        val intentUrl = "intent://callback?$query#Intent;package=$packageName;scheme=$scheme;end"
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(intentUrl)).build()
    }

    override fun revokeNotification(request: AppleRevokeNotification): ResponseEntity<Void> {
        revokeService.process(request)
        return ResponseEntity.ok().build()
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
