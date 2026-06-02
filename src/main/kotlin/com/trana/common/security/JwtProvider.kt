package com.trana.common.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

/**
 * JWT 토큰 발급/검증.
 *
 * - HMAC-SHA256 서명
 * - subject = userId (Long)
 * - issuer/audience 검증
 * - typ claim 으로 access / refresh 분리 (refactor v: refresh 가 access 자리 통과 차단)
 *
 * 보안:
 * - 토큰 자체는 평문 base64. 민감 정보를 claim 에 X
 * - secret 은 application yml 에서 주입, 운영은 환경변수
 */
@Component
class JwtProvider(
    private val properties: JwtProperties,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(properties.secret.toByteArray(Charsets.UTF_8))

    fun createAccessToken(userId: Long): String =
        buildToken(userId, properties.accessTokenTtl.seconds, TOKEN_TYPE_ACCESS)

    fun createRefreshToken(userId: Long): String =
        buildToken(userId, properties.refreshTokenTtl.seconds, TOKEN_TYPE_REFRESH)

    /** access 토큰 검증 + userId 추출. refresh 토큰 받으면 JwtException. */
    fun extractUserIdFromAccessToken(token: String): Long = verify(token, TOKEN_TYPE_ACCESS).subject.toLong()

    /** refresh 토큰 검증 + userId 추출. access 토큰 받으면 JwtException. */
    fun extractUserIdFromRefreshToken(token: String): Long = verify(token, TOKEN_TYPE_REFRESH).subject.toLong()

    /**
     * 토큰 검증 + typ 매칭 + Claims 반환.
     *
     * @throws JwtException 위변조/만료/issuer/audience 불일치 + typ 불일치
     */
    private fun verify(
        token: String,
        expectedType: String,
    ): Claims {
        val claims =
            Jwts
                .parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer)
                .requireAudience(properties.audience)
                .build()
                .parseSignedClaims(token)
                .payload
        val typ = claims[TOKEN_TYPE_CLAIM]?.toString()
        if (typ != expectedType) {
            throw JwtException("Expected token type=$expectedType but got typ=$typ")
        }
        return claims
    }

    private fun buildToken(
        userId: Long,
        ttlSeconds: Long,
        tokenType: String,
    ): String {
        val now = Instant.now()
        val expiration = now.plusSeconds(ttlSeconds)
        return Jwts
            .builder()
            .issuer(properties.issuer)
            .audience()
            .add(properties.audience)
            .and()
            .subject(userId.toString())
            .claim(TOKEN_TYPE_CLAIM, tokenType)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(key, Jwts.SIG.HS256)
            .compact()
    }

    companion object {
        private const val TOKEN_TYPE_CLAIM = "typ"
        private const val TOKEN_TYPE_ACCESS = "access"
        private const val TOKEN_TYPE_REFRESH = "refresh"
    }
}
