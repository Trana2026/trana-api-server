package com.trana.common.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * CORS 허용 origin / origin pattern 목록.
 *
 * - allowedOrigins: 정확 매치 (예: https://trana-web-guardian.vercel.app)
 * - allowedOriginPatterns: 와일드카드 매치 (예: https://trana-web-guardian-*.vercel.app)
 *   allowCredentials=true 와 와일드카드를 같이 쓰려면 patterns 쪽을 써야 함 (Spring 제약).
 *
 * yml 주입 예:
 *   trana.cors:
 *     allowed-origins:
 *       - http://localhost:3000
 *     allowed-origin-patterns:
 *       - https://trana-web-guardian-*.vercel.app
 */
@ConfigurationProperties(prefix = "trana.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = emptyList(),
    val allowedOriginPatterns: List<String> = emptyList(),
)
