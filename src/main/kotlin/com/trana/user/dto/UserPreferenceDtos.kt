package com.trana.user.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "푸시 알림 토글 요청")
data class UpdatePushEnabledRequest(
    @Schema(
        description = "푸시 알림 수신 동의 여부. false 시 NotificationDispatchService 가 발송 skip + 로그.",
        example = "false",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val enabled: Boolean,
)

@Schema(description = "푸시 알림 토글 응답")
data class PushEnabledResponse(
    @Schema(description = "변경 후 현재 푸시 알림 수신 동의 여부", example = "false")
    val pushEnabled: Boolean,
)
