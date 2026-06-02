package com.trana.common.dev

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Profile

/**
 * 개발 전용 (local + Railway dev) 설정.
 *
 * - tokenKey: DevTokenController 의 X-Dev-Token-Key 헤더 검증값 (Railway dev 외부 노출 차단용)
 * - prod profile 에서는 bean 안 로드 (Controller 도 동일 @Profile)
 */
@Profile("local", "dev")
@ConfigurationProperties(prefix = "trana.dev")
data class DevProperties(
    val tokenKey: String,
)
