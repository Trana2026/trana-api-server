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
        /** 웹 스트림용 측정 ID(G-XXXX). 앱 스트림이면 대신 [firebaseAppId] 사용. */
        val measurementId: String = "",
        /** 앱(Android/iOS·Firebase) 스트림용 Firebase App ID(1:...:android:...). 설정 시 measurementId 대신 사용. */
        val firebaseAppId: String = "",
        val apiSecret: String = "",
        val endpoint: String = "https://www.google-analytics.com/mp/collect",
    )
}
