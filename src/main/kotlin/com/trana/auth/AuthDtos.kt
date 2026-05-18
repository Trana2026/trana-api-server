package com.trana.auth

import com.trana.auth.oauth.SocialProvider
import com.trana.user.entity.AgeGroup
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "소셜 로그인 요청 — 미성년자 가입 전용 (성인은 KYC 흐름)")
data class SocialSignInRequest(
    @Schema(description = "소셜 공급자", example = "KAKAO", requiredMode = Schema.RequiredMode.REQUIRED)
    val provider: SocialProvider,
    @Schema(description = "공급자 id_token (OIDC JWT)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "idToken은 필수입니다")
    val idToken: String,
    @Schema(
        description = "연령대 — 현재 MINOR만 허용 (성인은 KYC 흐름)",
        example = "MINOR",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val ageGroup: AgeGroup,
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

@Schema(description = "토큰 재발급 요청")
data class RefreshRequest(
    @Schema(
        description = "Refresh token (sign-in 시 발급된 것)",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    @NotBlank(message = "refreshToken은 필수입니다")
    val refreshToken: String,
)
