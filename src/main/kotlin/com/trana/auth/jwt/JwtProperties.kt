package com.trana.auth.jwt

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * JWT 설정 — application.yml의 trana.jwt.* 매핑.
 *
 * - issuer/audience: 토큰의 iss/aud claim
 * - secret: HMAC-SHA256 서명 키 (최소 32 bytes / 256 bits)
 * - accessTokenTtl/refreshTokenTtl: ISO-8601 Duration (PT15M, P30D 등)
 */
@ConfigurationProperties(prefix = "trana.jwt")
data class JwtProperties(
    val issuer: String,
    val audience: String,
    val secret: String,
    val accessTokenTtl: Duration,
    val refreshTokenTtl: Duration,
)
