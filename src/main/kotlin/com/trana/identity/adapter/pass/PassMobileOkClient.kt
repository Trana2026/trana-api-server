package com.trana.identity.adapter.pass

import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.net.http.HttpClient
import java.time.Duration

/**
 * mobileOK 본인확인 검증 결과 요청 클라이언트.
 *
 * - URL: ${properties.mobileOkBaseUrl}/gui/service/v1/result/request
 * - 표준창 발급 토큰의 TTL 5초 안에 호출 필수 → connect 2s + read 3s = 5s budget
 * - 비동기 큐 사용 X — 호출자 (PassReturnService) 가 동기 호출
 */
@Component
class PassMobileOkClient(
    private val properties: PassProperties,
) {
    private val restClient: RestClient =
        RestClient
            .builder()
            .requestFactory(
                JdkClientHttpRequestFactory(
                    HttpClient
                        .newBuilder()
                        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                        .build(),
                ).apply {
                    setReadTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                },
            ).build()

    /**
     * encryptMOKKeyToken → encryptMOKResult 교환.
     *
     * @throws RestClientResponseException 4xx/5xx
     * @throws RestClientException 네트워크 오류 / timeout
     */
    fun requestResult(encryptMOKKeyToken: String): MobileOkResultResponse =
        restClient
            .post()
            .uri(properties.resultRequestUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .body(MobileOkResultRequest(encryptMOKKeyToken))
            .retrieve()
            .body<MobileOkResultResponse>()
            ?: error("mobileOK result/request 응답이 비어 있음")

    private data class MobileOkResultRequest(
        val encryptMOKKeyToken: String,
    )

    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 2L
        private const val READ_TIMEOUT_SECONDS = 3L
    }
}

/**
 * mobileOK 검증결과 요청 응답 (스펙 그대로).
 *
 * - resultCode "2000" = 성공, 그 외 실패
 * - encryptMOKResult: '|' 로 분리된 RSA(keyIv+hash) | AES(data) — PassResultDecryptor 가 복호화
 */
data class MobileOkResultResponse(
    val resultCode: String,
    val resultMsg: String? = null,
    val serviceId: String? = null,
    val encryptMOKResult: String? = null,
)
