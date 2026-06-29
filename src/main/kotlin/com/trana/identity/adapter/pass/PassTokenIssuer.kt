package com.trana.identity.adapter.pass

import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID

/**
 * mobileOK V3 표준창 토큰 발급.
 *
 * 책임:
 * - clientTxId 생성 (20~40자, "TRANA-" + UUID hex 32자 = 38자)
 * - 요청 토큰 JSON 생성 ({ version: V2, clientTxId, requestTime: yyyyMMddHHmmss KST })
 * - JSON → RSA-OAEP(SHA-256, MGF1) 암호화 (mok_keyInfo.dat 의 ServerPublicKey 사용)
 * - Base64 인코딩 → encryptReqClientInfo
 *
 * MOKReqClientInfo (외부 응답 구조) 의 wrapping 은 Service 가 담당 — 본 클래스는 encryptReqClientInfo 한 필드만.
 */
@Component
class PassTokenIssuer(
    private val keyInfoLoader: PassKeyInfoLoader,
    private val objectMapper: ObjectMapper,
) {
    /**
     * 새 clientTxId 발급. 매 PASS 가입 요청마다 unique.
     * 형식: "TRANA-" + UUID hex 32자 (총 38자, spec 20~40자 안에 들어감).
     */
    fun generateClientTxId(): String {
        val hex = UUID.randomUUID().toString().replace("-", "")
        return CLIENT_TX_ID_PREFIX + hex
    }

    /**
     * 주어진 clientTxId 로 encryptReqClientInfo 생성.
     *
     * caller (Service) 가 먼저 clientTxId 발급 + DB 에 PENDING verification 저장한 뒤
     * 이 메서드 호출 → MOKReqClientInfo 응답에 끼움.
     */
    fun encryptReqClientInfo(clientTxId: String): String {
        val payload =
            ReqClientInfoPayload(
                version = MOBILE_OK_VERSION,
                clientTxId = clientTxId,
                requestTime = nowKstTimestamp(),
            )
        val jsonBytes = objectMapper.writeValueAsBytes(payload)
        val publicKey = keyInfoLoader.get().serverPublicKey
        val encrypted = PassCryptoUtil.rsaOaepEncrypt(jsonBytes, publicKey)
        return Base64.getEncoder().encodeToString(encrypted)
    }

    private fun nowKstTimestamp(): String = ZonedDateTime.now(KST_ZONE).format(TIMESTAMP_FORMATTER)

    /**
     * mobileOK 요청 토큰 JSON 페이로드. 직렬화는 ObjectMapper (snake_case 변환 X — 그대로 사용).
     */
    private data class ReqClientInfoPayload(
        val version: String,
        val clientTxId: String,
        val requestTime: String,
    )

    companion object {
        private const val CLIENT_TX_ID_PREFIX = "TRANA-"
        private const val MOBILE_OK_VERSION = "V2"
        private val KST_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        private val TIMESTAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    }
}
