package com.trana.auth

import com.trana.user.SocialProvider
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "소셜 로그인 요청")
data class SocialSignInRequest(
    @Schema(
        description = "소셜 공급자",
        example = "KAKAO",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val provider: SocialProvider,

    @Schema(
        description = "공급자 access_token (Flutter SDK가 받아온 값)",
        example = "kakao_access_token_string_here",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val accessToken: String,
)

@Schema(description = "로그인 응답 — 가입/로그인 성공 시 반환")
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
