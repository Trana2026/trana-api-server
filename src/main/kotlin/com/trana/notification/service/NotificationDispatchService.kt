package com.trana.notification.service

import com.trana.user.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 푸시 발송 진입점 — User.pushEnabled 검사 후 FcmDispatchService 위임.
 *
 * 흐름:
 * - User 조회 → pushEnabled=false 면 skip + 로그
 * - pushEnabled=true → FcmDispatchService.sendToUser 위임 (멀티캐스트 + invalid 정리)
 *
 * 호출 측 (KycGuardianService 등) 은 NotificationDispatchService 만 의존 — pushEnabled 검사 책임 분리.
 *
 * 운영 보류 (W9+):
 * - 카테고리별 토글 (계약 / 마케팅 / 시스템) — 현재는 전체 토글만
 * - 조용 시간대 (DnD) — 사용자 정의 시간 외 발송 차단
 */
@Service
class NotificationDispatchService(
    private val userService: UserService,
    private val fcmDispatchService: FcmDispatchService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sendToUser(
        userId: Long,
        title: String,
        body: String,
        deeplink: String? = null,
        data: Map<String, String> = emptyMap(),
    ) {
        val user = userService.getById(userId)
        if (!user.pushEnabled) {
            log.info("[NotificationDispatch] skip — pushEnabled=false. userId={}", userId)
            return
        }
        fcmDispatchService.sendToUser(
            userId = userId,
            title = title,
            body = body,
            deeplink = deeplink,
            data = data,
        )
    }
}
