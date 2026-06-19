package com.trana.auth.oauth

import com.trana.auth.AuthException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * Apple Sign In id_token 검증 어댑터.
 *
 * Flutter SDK (`sign_in_with_apple`) 흐름:
 * - 클라이언트가 raw nonce 생성 → SHA256 hash 를 Apple authorize 요청에 첨부
 * - Apple 응답의 id_token 에 nonce claim = 그 SHA256 hex 값
 * - 백엔드로 raw nonce + idToken 같이 전송 (raw nonce 는 Apple 에 안 보냄)
 *
 * 검증:
 * - JwtDecoder (Apple JWKS RS256, multi-audience: Bundle ID + Services ID)
 * - nonce: SHA256(raw nonce) == id_token 의 nonce claim (replay 방지)
 * - sub = Apple 영구 user ID
 *
 * Apple 특수 claim:
 * - email: optional, `@privaterelay.appleid.com` 가능 (private relay)
 * - email_verified: 문자열 "true" (boolean 아님)
 * - is_private_email: 문자열 "true" 면 사용자가 hide my email 선택
 *
 * 이름은 id_token 에 없음 — OAuth `user` 객체 (최초 로그인 1회) 로만 옴.
 * MVP: name null. 후속 작업 (Apple-6 callback endpoint) 에서 user 객체로 name 처리.
 */
@Component
class AppleAuthAdapter(
    @Qualifier("appleIdTokenDecoder")
    private val jwtDecoder: JwtDecoder,
) : SocialAuthAdapter {
    override val provider = SocialProvider.APPLE

    override fun verify(
        idToken: String,
        nonce: String?,
    ): SocialUserInfo {
        val rawNonce = nonce ?: throwInvalid()
        val jwt = decodeOrThrow(idToken)
        verifyNonce(rawNonce, jwt.getClaimAsString("nonce"))
        val sub = jwt.subject ?: throwInvalid()

        return SocialUserInfo(
            provider = SocialProvider.APPLE,
            providerUserId = sub,
            email = jwt.getClaimAsString("email"),
            name = null,
        )
    }

    private fun decodeOrThrow(idToken: String): Jwt =
        try {
            jwtDecoder.decode(idToken)
        } catch (ex: JwtException) {
            throw AuthException.InvalidSocialToken(SocialProvider.APPLE, cause = ex)
        }

    private fun verifyNonce(
        rawNonce: String,
        claim: String?,
    ) {
        val tokenNonce = claim ?: throwInvalid()
        if (sha256Hex(rawNonce) != tokenNonce) throwInvalid()
    }

    private fun throwInvalid(): Nothing = throw AuthException.InvalidSocialToken(SocialProvider.APPLE)

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
