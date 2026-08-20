package com.trana.common.demo

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 데모 모드 설정 — App Store 심사 대응.
 *
 * - enabled=true 면 앱 인트로에서 테스트 로그인 진입(롱프레스)을 활성화하도록 신호.
 * - Railway 환경변수 TRANA_DEMO_ENABLED 로 토글 (심사 기간 true, 승인 후 false).
 * - 앱 코드 수정/재배포 없이 서버 값 하나로 여닫음.
 */
@ConfigurationProperties(prefix = "trana.demo")
data class DemoProperties(
    val enabled: Boolean = false,
)
