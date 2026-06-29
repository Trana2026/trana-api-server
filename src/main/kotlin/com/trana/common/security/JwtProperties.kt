package com.trana.common.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * JWT 설정 — application.yml의 trana.jwt.* 매핑.
 *
 * - issuer/audience: 토큰의 iss/aud claim
 * - secret: HMAC-SHA256 서명 키 (최소 32 bytes / 256 bits)
 * - accessTokenTtl/refreshTokenTtl: ISO-8601 Duration
 *   - accessTokenTtl: PT15M (15분)
 *   - refreshTokenTtl: P1825D (5년) — PASS 도입 후 Flutter 가 PIN/생체로 로컬 unlock 책임 (PASS-7)
 *     분실/탈취 시 전체 무효화는 후속 task (refresh token versioning + 무효화 endpoint)
 */
@ConfigurationProperties(prefix = "trana.jwt")
data class JwtProperties(
    val issuer: String,
    val audience: String,
    val secret: String,
    val accessTokenTtl: Duration,
    val refreshTokenTtl: Duration,
)
