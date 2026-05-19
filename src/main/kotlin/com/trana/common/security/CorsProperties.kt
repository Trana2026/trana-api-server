package com.trana.common.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * CORS 허용 origin 목록.
 *
 * - local: [http://localhost:3000]
 * - dev:   [https://trana-web-guardian.vercel.app, http://localhost:3000]
 * - prod:  Vercel production만 (W7~)
 *
 * yml에서 list 형태로 주입:
 *   trana.cors.allowed-origins:
 *     - http://localhost:3000
 */
@ConfigurationProperties(prefix = "trana.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = emptyList(),
)
