package com.trana.notification.adapter.ipinfo

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * 개발 단계 mock — 실제 조회 X, 항상 null 반환 (등록 흐름 정상, location 필드만 비어있음).
 *
 * 활성 조건: `ipinfo-live` profile 이 **꺼져있을 때** (기본값).
 * - local/dev/test/prod 모두 기본 Mock
 * - Live 조회 활성화: `SPRING_PROFILES_ACTIVE=...,ipinfo-live` 추가 + `TRANA_IPINFO_TOKEN` env
 * - [LiveIpInfoClient] 와 `!ipinfo-live` / `ipinfo-live` 로 상호 배타
 */
@Component
@Profile("!ipinfo-live")
class MockIpInfoClient : IpInfoClient {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun lookup(ip: String): IpLocationResult? {
        log.info("[MOCK IPINFO] lookup ip={} → null (mock)", ip)
        return null
    }
}
