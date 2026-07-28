package com.trana.contract

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 계약 도메인 web URL 설정 (refactor j/hh).
 *
 * - baseUrl: 계약 web(랜딩) 진입점. 알림톡 AL 버튼의 linkMo/linkPc(미설치·PC 폴백)에 사용.
 * - appLinkBase: 알림톡 AL 버튼의 linkAnd/linkIos(앱 스킴) prefix.
 *   앱 매니페스트/Info.plist 등록값과 일치해야 함 (Android 커스텀 스킴 scheme=trana host=trana.kr / iOS scheme=trana).
 *   → `trana://trana.kr` + 경로(웹과 1:1). deeplink.md 참조.
 */
@ConfigurationProperties(prefix = "trana.contract.web")
data class ContractWebProperties(
    val baseUrl: String,
    val appLinkBase: String = "trana://trana.kr",
)
