package com.trana.notification.adapter.fcm

import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Live FCM 발송 — Firebase Admin SDK 9.9.0 의 sendEachForMulticast 사용.
 *
 * 활성 조건: `fcm-live` profile 켜짐 + FirebaseConfig 가 FirebaseApp 빈 등록.
 *
 * 다중 토큰 (한 유저의 multi-device) 한 번에 발송 — 최대 500 토큰 / 요청 (FCM 제한).
 * 응답의 per-token result 에서 UNREGISTERED / INVALID_ARGUMENT 면
 * invalidTokens 리스트에 모음 → C-5 가 DB 정리.
 *
 * deeplink 는 FCM data 필드의 "deeplink" 키로 전달 — Flutter 가 받아서 라우팅.
 */
@Component
@Profile("fcm-live")
class LiveFcmClient(
    firebaseApp: FirebaseApp,
) : FcmClient {
    private val log = LoggerFactory.getLogger(javaClass)
    private val messaging = FirebaseMessaging.getInstance(firebaseApp)

    override fun send(message: FcmMessage): FcmSendResult {
        if (message.tokens.isEmpty()) {
            return FcmSendResult(successCount = 0, failureCount = 0)
        }
        require(message.tokens.size <= FCM_MULTICAST_LIMIT) {
            "tokens.size 가 FCM 멀티캐스트 한 번 한계 ($FCM_MULTICAST_LIMIT) 초과 — chunk 처리 필요"
        }

        val multicast =
            MulticastMessage
                .builder()
                .addAllTokens(message.tokens)
                .setNotification(buildNotification(message))
                .putAllData(buildData(message))
                .build()

        val batch = messaging.sendEachForMulticast(multicast)
        val invalidTokens =
            batch.responses
                .mapIndexedNotNull { index, resp ->
                    if (!resp.isSuccessful && isInvalidToken(resp.exception)) {
                        message.tokens[index]
                    } else {
                        null
                    }
                }

        if (invalidTokens.isNotEmpty()) {
            log.info("[FCM] invalid tokens detected: count={}", invalidTokens.size)
        }

        return FcmSendResult(
            successCount = batch.successCount,
            failureCount = batch.failureCount,
            invalidTokens = invalidTokens,
        )
    }

    private fun buildNotification(message: FcmMessage): Notification =
        Notification
            .builder()
            .setTitle(message.title)
            .setBody(message.body)
            .build()

    private fun buildData(message: FcmMessage): Map<String, String> =
        message.data.toMutableMap().apply {
            message.deeplink?.let { put("deeplink", it) }
        }

    private fun isInvalidToken(ex: Throwable?): Boolean {
        val firebaseEx = ex as? FirebaseMessagingException ?: return false
        return firebaseEx.messagingErrorCode == MessagingErrorCode.UNREGISTERED ||
            firebaseEx.messagingErrorCode == MessagingErrorCode.INVALID_ARGUMENT
    }

    companion object {
        private const val FCM_MULTICAST_LIMIT = 500
    }
}
