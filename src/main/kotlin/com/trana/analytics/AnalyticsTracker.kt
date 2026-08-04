package com.trana.analytics

/**
 * 서버측 분석 이벤트 전송 추상화 (GA4 + Amplitude 통합 wrapper).
 *
 * 화면(클라이언트)이 아니라 **서버 권위 이벤트**를 여기로 보낸다.
 * 구현: [LogAnalyticsTracker](Mock, 기본) / LiveAnalyticsTracker(`analytics-live`).
 * 전송 실패가 비즈니스 트랜잭션을 깨면 안 됨 → 구현체는 best-effort(예외 삼킴).
 */
interface AnalyticsTracker {
    fun track(event: AnalyticsEvent)
}
