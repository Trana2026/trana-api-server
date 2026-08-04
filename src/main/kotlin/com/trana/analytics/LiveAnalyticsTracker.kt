package com.trana.analytics

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper

/**
 * GA4(Measurement Protocol) + Amplitude(HTTP V2) 서버측 전송.
 *
 * 활성: `analytics-live` 프로파일 + 각 도구 자격증명 설정 시.
 * best-effort — 전송 실패는 로그만 남기고 삼킴(비즈니스 트랜잭션 보호).
 * PII 는 [AnalyticsEvent] 계약상 애초에 안 담김.
 */
@Component
@Profile("analytics-live")
class LiveAnalyticsTracker(
    private val properties: AnalyticsProperties,
    private val objectMapper: ObjectMapper,
) : AnalyticsTracker {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = RestClient.create()

    override fun track(event: AnalyticsEvent) {
        sendAmplitude(event)
        sendGa4(event)
    }

    private fun sendAmplitude(event: AnalyticsEvent) {
        val key = properties.amplitude.apiKey
        if (key.isBlank()) return
        val payload =
            mapOf(
                "api_key" to key,
                "events" to
                    listOf(
                        buildMap {
                            put("event_type", event.name)
                            put("insert_id", event.eventId) // 중복 제거
                            event.userId?.let { put("user_id", it.toString()) }
                            put("event_properties", event.properties.filterValues { it != null })
                        },
                    ),
            )
        post(properties.amplitude.endpoint, payload, "amplitude", event.name)
    }

    private fun sendGa4(event: AnalyticsEvent) {
        val mid = properties.ga4.measurementId
        val secret = properties.ga4.apiSecret
        if (mid.isBlank() || secret.isBlank()) return
        val clientId = event.userId?.let { "server.$it" } ?: "server.anonymous"
        val params =
            event.properties.filterValues { it != null }.toMutableMap().apply {
                put("event_id", event.eventId) // 클라이언트와 dedup 위한 공통 id
            }
        val payload =
            buildMap {
                put("client_id", clientId)
                event.userId?.let { put("user_id", it.toString()) }
                put("events", listOf(mapOf("name" to (event.gaEventName ?: event.name), "params" to params)))
            }
        val url = "${properties.ga4.endpoint}?measurement_id=$mid&api_secret=$secret"
        post(url, payload, "ga4", event.name)
    }

    private fun post(
        url: String,
        payload: Any,
        target: String,
        eventName: String,
    ) {
        try {
            restClient
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(payload))
                .retrieve()
                .toBodilessEntity()
        } catch (e: org.springframework.web.client.RestClientException) {
            log.warn("[ANALYTICS] {} {} 전송 실패(무시) — {}", target, eventName, e.message)
        }
    }
}
