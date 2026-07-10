package com.trana.notification.adapter.ipinfo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.net.http.HttpClient
import java.time.Duration

/**
 * ipinfo.io 실호출 클라이언트.
 *
 * 활성 조건: `ipinfo-live` profile.
 * - 기본 (모든 profile) 은 [MockIpInfoClient] 가 동작
 * - 활성화: `SPRING_PROFILES_ACTIVE=...,ipinfo-live` + `TRANA_IPINFO_TOKEN` env
 *
 * API: `GET https://ipinfo.io/{ip}` + `Authorization: Bearer <TOKEN>`
 * - 응답 JSON: `{"ip", "city", "region", "country", "loc", ...}`
 * - 사설/특수 IP 는 `{"bogon": true}` 반환 → null 처리
 * - 실패 (rate limit / 네트워크 / 파싱 에러) → warn 로그 + null (등록 흐름 정상 진행)
 * - 무료 tier 50k req/month HTTPS
 * - timeout 3s / 3s — 등록 흐름 latency 최소화 우선
 */
@Component
@Profile("ipinfo-live")
class LiveIpInfoClient(
    private val ipInfoProperties: IpInfoProperties,
) : IpInfoClient {
    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient: RestClient =
        RestClient
            .builder()
            .baseUrl(ipInfoProperties.baseUrl)
            .requestFactory(buildRequestFactory())
            .build()

    override fun lookup(ip: String): IpLocationResult? {
        if (ipInfoProperties.token.isBlank()) {
            log.warn("[IPINFO] token blank — skip. ip={}", ip)
            return null
        }
        return runCatching {
            restClient
                .get()
                .uri("/{ip}", ip)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${ipInfoProperties.token}")
                .retrieve()
                .body<IpInfoResponse>()
                ?.let {
                    if (it.bogon == true) {
                        log.info("[IPINFO] bogon ip={} → null", ip)
                        null
                    } else {
                        log.info("[IPINFO] lookup ip={} → city={} country={}", ip, it.city, it.country)
                        IpLocationResult(city = it.city, country = it.country)
                    }
                }
        }.onFailure {
            log.warn("[IPINFO] lookup fail ip={}: {}", ip, it.message)
        }.getOrNull()
    }

    private fun buildRequestFactory(): JdkClientHttpRequestFactory {
        val httpClient =
            HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build()
        return JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 3L
        private const val READ_TIMEOUT_SECONDS = 3L
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class IpInfoResponse(
    val ip: String? = null,
    val city: String? = null,
    val region: String? = null,
    val country: String? = null,
    val bogon: Boolean? = null,
)
