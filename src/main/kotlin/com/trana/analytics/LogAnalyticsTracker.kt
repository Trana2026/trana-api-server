package com.trana.analytics

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * 기본(개발/미설정) 분석 tracker — 실제 전송 없이 로그만.
 * 활성: `analytics-live` 프로파일이 **꺼져있을 때**(기본). Live 는 [LiveAnalyticsTracker].
 */
@Component
@Profile("!analytics-live")
class LogAnalyticsTracker : AnalyticsTracker {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun track(event: AnalyticsEvent) {
        log.info(
            "[ANALYTICS] {} (ga={}, eventId={}, userId={}, props={})",
            event.name,
            event.gaEventName ?: event.name,
            event.eventId,
            event.userId,
            event.properties,
        )
    }
}
