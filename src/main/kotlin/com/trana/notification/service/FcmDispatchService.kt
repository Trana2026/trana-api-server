package com.trana.notification.service

import com.trana.common.crypto.Sha256Hasher
import com.trana.notification.adapter.fcm.FcmClient
import com.trana.notification.adapter.fcm.FcmMessage
import com.trana.notification.repository.DeviceTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.encrypt.TextEncryptor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * userId 기준 FCM 발송 — 모든 활성 device_token 조회 → 복호화 → 멀티캐스트 send → invalid 정리.
 *
 * - 호출 측 (C-A 가이드: KycGuardianService 등) 은 userId + 메시지만 넘김
 * - 토큰 복호화 + 메시지 빌드 + invalid hash 정리는 모두 여기서 처리
 * - LiveFcmClient 의 @Retryable 이 5xx / timeout 재시도 — 여기서는 send 결과만 수신
 *
 * 운영 보류 (W7+):
 * - 90s 트랜잭션 점유 — @Async 또는 별도 스레드풀 분리
 * - 대량 발송 시 chunk (500 token 한계) — 현재 단일 호출만
 */
@Service
@Transactional
class FcmDispatchService(
    private val fcmClient: FcmClient,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val textEncryptor: TextEncryptor,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sendToUser(
        userId: Long,
        title: String,
        body: String,
        deeplink: String? = null,
        data: Map<String, String> = emptyMap(),
    ) {
        val tokens = deviceTokenRepository.findAllByUserId(userId)
        if (tokens.isEmpty()) {
            log.info("[FCM] sendToUser skip — no tokens. userId={}", userId)
            return
        }

        val decrypted = tokens.map { textEncryptor.decrypt(it.tokenEncrypted) }
        val result =
            fcmClient.send(
                FcmMessage(
                    tokens = decrypted,
                    title = title,
                    body = body,
                    deeplink = deeplink,
                    data = data,
                ),
            )

        log.info(
            "[FCM] sendToUser userId={} success={}/failure={} invalidCount={}",
            userId,
            result.successCount,
            result.failureCount,
            result.invalidTokens.size,
        )

        if (result.invalidTokens.isNotEmpty()) {
            val invalidHashes = result.invalidTokens.map(Sha256Hasher::hashHex)
            val deleted = deviceTokenRepository.deleteAllByTokenHashIn(invalidHashes)
            log.info("[FCM] invalid tokens 정리 userId={} deleted={}", userId, deleted)
        }
    }
}
