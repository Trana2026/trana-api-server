package com.trana.user.dto

import com.trana.user.entity.AgeGroup
import com.trana.user.entity.UserStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "본인 정보 응답")
data class MeResponse(
    @Schema(description = "외부 노출용 식별자 (nanoid 12자)", example = "Vh7sK2x9Pq3R")
    val publicCode: String,
    @Schema(description = "이메일", nullable = true)
    val email: String?,
    @Schema(description = "닉네임", nullable = true)
    val nickname: String?,
    @Schema(description = "사용자 상태", example = "ACTIVE")
    val status: UserStatus,
    @Schema(description = "연령대 (KYC 또는 소셜 가입 시 결정). null=가입 미완", nullable = true)
    val ageGroup: AgeGroup?,
    @Schema(description = "보호자 인증 완료 시각 (MINOR만 의미). null=미인증", nullable = true)
    val guardianVerifiedAt: Instant?,
)
