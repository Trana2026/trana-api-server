package com.trana.notification.dto

import com.trana.notification.entity.DevicePlatform
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "FCM 디바이스 토큰 등록 요청 본문")
data class RegisterDeviceTokenRequest(
    @field:NotBlank
    @field:Schema(
        description =
            "FCM SDK 가 발급한 디바이스 토큰. 앱 최초 실행 또는 토큰 갱신 시 호출. " +
                "백엔드는 SHA-256 hash + AES-256-GCM 암호화 저장 (명세서 보안 정책).",
        example = "dXr8...실제FCM토큰...AB12",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val token: String,
    @field:Schema(
        description = "단말 플랫폼. ANDROID = FCM 직접, IOS = APNs 중계.",
        example = "ANDROID",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val platform: DevicePlatform,
)

@Schema(description = "FCM 디바이스 토큰 해제 요청 본문")
data class UnregisterDeviceTokenRequest(
    @field:NotBlank
    @field:Schema(
        description =
            "해제할 디바이스 토큰. 백엔드가 SHA-256 hash 로 매칭 후 삭제. " +
                "같은 token 의 다른 user row 는 영향 X (멱등).",
        example = "dXr8...실제FCM토큰...AB12",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val token: String,
)
