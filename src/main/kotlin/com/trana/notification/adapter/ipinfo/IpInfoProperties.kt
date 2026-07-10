package com.trana.notification.adapter.ipinfo

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * ipinfo.io API 설정.
 *
 * 활성 조건: `ipinfo-live` profile → [LiveIpInfoClient] 로 실호출.
 * 기본 (모든 profile) 은 [MockIpInfoClient] 가 동작.
 *
 * 발급:
 * - https://ipinfo.io/signup 무료 계정 → API token 발급
 * - 무료 tier: 50k req/month HTTPS, city/country/region 필드 포함
 *
 * 환경변수:
 * - `TRANA_IPINFO_TOKEN` (secret, Railway env 주입)
 *
 * 운영 정책:
 * - local: application-local.yml 에 직접 박음 (gitignore)
 * - dev/prod: Railway env
 */
@ConfigurationProperties(prefix = "trana.ipinfo")
data class IpInfoProperties(
    val token: String = "",
    val baseUrl: String = "https://ipinfo.io",
)
