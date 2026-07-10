package com.trana.notification.dto

import com.trana.notification.entity.DevicePlatform
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.time.Instant

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
    @field:Schema(
        description =
            "Flutter device_info_plus 로 식별한 기기 모델명. 마이페이지 기기 관리 UX 노출용. " +
                "앱 이전 버전 호환 위해 optional — 미전송 시 목록에 모델명 안 뜸",
        example = "iPhone 15 Pro",
        nullable = true,
    )
    val deviceModel: String? = null,
)

@Schema(description = "FCM 디바이스 토큰 등록 응답 — 마이페이지 '현재 단말' 식별용 id 반환")
data class RegisterDeviceTokenResponse(
    @Schema(
        description =
            "등록된 device_tokens.id. Flutter 가 secure storage 에 저장 → GET 목록의 id 와 비교해 " +
                "'현재 단말' 식별 (강제 해제 confirm UX). 같은 token 재등록 (멱등) 시에도 동일 id 반환",
        example = "12",
    )
    val id: Long,
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

@Schema(description = "마이페이지 기기 관리 — 단말 목록 단일 row")
data class DeviceTokenSummaryResponse(
    @Schema(description = "기기 식별자 — 강제 해제 시 사용", example = "12")
    val id: Long,
    @Schema(description = "단말 플랫폼", example = "ANDROID")
    val platform: DevicePlatform,
    @Schema(
        description = "Flutter 가 등록 시 전송한 기기 모델명. 앱 이전 버전 / 미전송 시 null",
        example = "iPhone 15 Pro",
        nullable = true,
    )
    val deviceModel: String?,
    @Schema(
        description = "ipinfo.io 로 등록 시 IP → 도시 조회 결과. 조회 실패 / 앱 이전 버전 시 null",
        example = "Seoul",
        nullable = true,
    )
    val locationCity: String?,
    @Schema(
        description = "ISO 3166-1 alpha-2 국가 코드. 조회 실패 / 앱 이전 버전 시 null",
        example = "KR",
        nullable = true,
    )
    val locationCountry: String?,
    @Schema(description = "등록 시각 (UTC)")
    val createdAt: Instant,
    @Schema(
        description =
            "마지막 활동 시각 (앱 foreground 진입 시 Flutter 가 ping endpoint 호출). " +
                "등록 직후 발송 이력 없는 신규 단말은 null",
        nullable = true,
    )
    val lastUsedAt: Instant?,
)

@Schema(description = "기기 활성 ping 요청 본문 — 앱 foreground 진입 시 호출 (멱등)")
data class PingDeviceTokenRequest(
    @field:NotBlank
    @field:Schema(
        description = "본인 단말의 FCM token. 백엔드가 SHA-256 hash 매칭 후 lastUsedAt 갱신. 본인 token 아니면 silent ignore (200)",
        example = "dXr8...실제FCM토큰...AB12",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val token: String,
)
