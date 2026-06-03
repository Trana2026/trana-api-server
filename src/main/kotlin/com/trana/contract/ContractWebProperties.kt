package com.trana.contract

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 계약 도메인 web URL 설정 (refactor j/hh).
 *
 * - baseUrl: 모바일 앱 / 계약 web 진입점 (이전 INVITATION_BASE_URL 하드코딩 대체)
 *   - local: http://localhost:3000 (모바일 앱 dev — 또는 모바일 dev URL)
 *   - dev:   https://trana.app (앱스토어 / 실 배포)
 */
@ConfigurationProperties(prefix = "trana.contract.web")
data class ContractWebProperties(
    val baseUrl: String,
)
