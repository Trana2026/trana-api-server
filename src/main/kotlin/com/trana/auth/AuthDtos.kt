package com.trana.auth

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "로그인/재발급 응답")
data class SignInResponse(
    @Schema(
        description = "우리 서버 발급 access token (15분 유효)",
        example = "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJ0cmFuYSIuLi4",
    )
    val accessToken: String,
    @Schema(
        description = "우리 서버 발급 refresh token (30일 유효)",
        example = "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJ0cmFuYSIuLi4",
    )
    val refreshToken: String,
    @Schema(
        description = "외부 노출용 사용자 식별자 (nanoid 12자)",
        example = "Vh7sK2x9Pq3R",
    )
    val publicCode: String,
)

@Schema(description = "토큰 재발급 요청")
data class RefreshRequest(
    @Schema(
        description = "Refresh token (sign-in 시 발급된 것)",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    @NotBlank(message = "refreshToken은 필수입니다")
    val refreshToken: String,
)

@Schema(description = "로그아웃 요청 — deviceToken 제공 시 해당 단말의 FCM 등록 같이 정리")
data class LogoutRequest(
    @Schema(
        description =
            "본인 단말의 FCM token. 제공 시 백엔드가 SHA-256 hash 매칭 후 device_tokens row 삭제 (멱등). " +
                "Flutter 가 단말 push 수신 중단 처리도 같이 원할 때 전송. " +
                "JWT 무효화는 X (stateless 정책, access 15분 자연 만료). audit 로그는 항상 기록.",
        example = "dXr8...실제FCM토큰...AB12",
        nullable = true,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
    )
    val deviceToken: String? = null,
)
