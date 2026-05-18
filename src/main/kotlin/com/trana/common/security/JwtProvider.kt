package com.trana.common.security

import io.jsonwebtoken.Claims
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
 *
 * 보안:
 * - 토큰 자체는 평문 base64. 민감 정보를 claim에 X
 * - secret은 application yml에서 주입, 운영은 환경변수
 */
@Component
class JwtProvider(
    private val properties: JwtProperties,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(properties.secret.toByteArray(Charsets.UTF_8))

    fun createAccessToken(userId: Long): String = buildToken(userId, properties.accessTokenTtl.seconds)

    fun createRefreshToken(userId: Long): String = buildToken(userId, properties.refreshTokenTtl.seconds)

    /**
     * 토큰 검증 + Claims 반환.
     *
     * @throws io.jsonwebtoken.JwtException 위변조/만료/issuer/audience 불일치
     */
    fun verify(token: String): Claims =
        Jwts
            .parser()
            .verifyWith(key)
            .requireIssuer(properties.issuer)
            .requireAudience(properties.audience)
            .build()
            .parseSignedClaims(token)
            .payload

    fun extractUserId(token: String): Long = verify(token).subject.toLong()

    private fun buildToken(
        userId: Long,
        ttlSeconds: Long,
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
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(key, Jwts.SIG.HS256)
            .compact()
    }
}
