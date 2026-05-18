package com.trana.user

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "본인 정보 응답")
data class MeResponse(
    @Schema(
        description = "외부 노출용 사용자 식별자 (nanoid 12자)",
        example = "Vh7sK2x9Pq3R",
    )
    val publicCode: String,
    @Schema(
        description = "이메일 (OAuth 공급자가 제공한 경우)",
        example = "user@example.com",
        nullable = true,
    )
    val email: String?,
    @Schema(
        description = "닉네임 (OAuth 또는 사용자 입력)",
        example = "홍길동",
        nullable = true,
    )
    val nickname: String?,
    @Schema(
        description = "사용자 상태",
        example = "ACTIVE",
    )
    val status: UserStatus,
    @Schema(
        description = "연령대 (KYC 또는 자기보고로 결정). null=미정",
        example = "ADULT",
        nullable = true,
    )
    val ageGroup: AgeGroup?,
    @Schema(
        description = "보호자 인증 완료 시각 (MINOR만 의미 있음). null=미인증",
        nullable = true,
    )
    val guardianVerifiedAt: Instant?,
)
