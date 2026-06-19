package com.trana.user.dto

import com.trana.user.entity.AgeGroup
import com.trana.user.entity.Gender
import com.trana.user.entity.UserStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "본인 정보 응답")
data class MeResponse(
    @Schema(description = "외부 노출용 식별자 (nanoid 12자)", example = "Vh7sK2x9Pq3R")
    val publicCode: String,
    @Schema(description = "이메일", nullable = true)
    val email: String?,
    @Schema(description = "사용자 상태", example = "ACTIVE")
    val status: UserStatus,
    @Schema(description = "연령대 (KYC 또는 소셜 가입 시 결정). null=가입 미완", nullable = true)
    val ageGroup: AgeGroup?,
    @Schema(description = "보호자 인증 완료 시각 (MINOR만 의미). null=미인증", nullable = true)
    val guardianVerifiedAt: Instant?,
    @Schema(description = "이름 — 성인: KYC 실명 / 미성년: 소셜 표시명. null=KYC 미완 미성년", nullable = true)
    val name: String?,
    @Schema(description = "생년월일 (YYYY-MM-DD). KYC 사용자만 — 미성년 소셜 가입자는 null", nullable = true, example = "1990-01-01")
    val birthDate: String?,
    @Schema(description = "성별. KYC 사용자만 — 미성년 소셜 가입자는 null", nullable = true)
    val gender: Gender?,
    @Schema(description = "휴대폰 번호. KYC 사용자만 — 미성년 소셜 가입자는 null", nullable = true, example = "010-1234-5678")
    val phone: String?,
    @Schema(description = "푸시 알림 수신 동의 여부. 기본 true, /v1/users/me/push-enabled 로 변경", example = "true")
    val pushEnabled: Boolean,
)
