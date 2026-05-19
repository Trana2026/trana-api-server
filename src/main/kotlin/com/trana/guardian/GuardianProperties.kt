package com.trana.guardian

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 보호자 도메인 설정.
 *
 * - webBaseUrl: trana-web-guardian 배포 URL. 응답의 verifyUrl 조합용
 *   - local: http://localhost:3000
 *   - dev:   https://trana-web-guardian.vercel.app
 */
@ConfigurationProperties(prefix = "trana.guardian")
data class GuardianProperties(
    val webBaseUrl: String,
)
