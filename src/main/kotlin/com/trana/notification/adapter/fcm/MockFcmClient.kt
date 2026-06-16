package com.trana.notification.adapter.fcm

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * 개발/단말 통합 대기 단계 mock — 실제 발송 X, log 로 의도만 기록.
 *
 * 활성 조건: `fcm-live` profile 이 **꺼져있을 때** (기본값).
 * - local/dev/test/prod 모두 기본 Mock
 * - Live 발송 활성화: `SPRING_PROFILES_ACTIVE=...,fcm-live` 추가
 * - [LiveFcmClient] 와 `!fcm-live` / `fcm-live` 로 상호 배타
 */
@Component
@Profile("!fcm-live")
class MockFcmClient : FcmClient {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(message: FcmMessage): FcmSendResult {
        if (message.tokens.isEmpty()) {
            log.info("[MOCK FCM] send → tokens empty, skip")
            return FcmSendResult(successCount = 0, failureCount = 0)
        }
        log.info(
            "[MOCK FCM] send → tokens={}, title=\"{}\", body=\"{}\", deeplink={}, data={}",
            message.tokens.map(::maskToken),
            message.title,
            message.body,
            message.deeplink ?: "(none)",
            message.data,
        )
        return FcmSendResult(successCount = message.tokens.size, failureCount = 0)
    }

    /** abcdef1234... → abcdef12...(40c) 토큰 식별만 가능하게 앞 8자 + 길이 */
    private fun maskToken(token: String): String =
        if (token.length <= MASK_PREFIX) {
            "***"
        } else {
            "${token.take(MASK_PREFIX)}...(${token.length}c)"
        }

    companion object {
        private const val MASK_PREFIX = 8
    }
}
