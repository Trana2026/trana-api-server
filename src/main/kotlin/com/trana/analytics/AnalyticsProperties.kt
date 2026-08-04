package com.trana.analytics

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 서버측 분석(GA4 Measurement Protocol + Amplitude HTTP) 설정.
 *
 * - 기본 비활성(Mock=LogAnalyticsTracker). Live 발송은 `analytics-live` 프로파일.
 * - 시크릿(apiKey/apiSecret)은 env 로만 주입.
 */
@ConfigurationProperties(prefix = "trana.analytics")
data class AnalyticsProperties(
    val amplitude: Amplitude = Amplitude(),
    val ga4: Ga4 = Ga4(),
) {
    data class Amplitude(
        val apiKey: String = "",
        val endpoint: String = "https://api2.amplitude.com/2/httpapi",
    )

    data class Ga4(
        val measurementId: String = "",
        val apiSecret: String = "",
        val endpoint: String = "https://www.google-analytics.com/mp/collect",
    )
}
