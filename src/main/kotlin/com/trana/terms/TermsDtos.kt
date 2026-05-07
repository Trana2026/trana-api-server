package com.trana.terms

import com.trana.user.AgeGroup
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(description = "활성 약관 응답")
data class TermsResponse(
    @Schema(description = "약관 ID", example = "1")
    val id: Long,

    @Schema(description = "약관 유형", example = "SERVICE")
    val type: TermsType,

    @Schema(description = "약관 버전", example = "1.0")
    val version: String,

    @Schema(description = "약관 제목", example = "TRANA 서비스 이용약관")
    val title: String,

    @Schema(description = "약관 본문 URL", example = "https://trana.com/terms/service/1.0")
    val contentUrl: String,

    @Schema(description = "시행 시각 (ISO-8601)", example = "2026-05-01T00:00:00Z")
    val effectiveAt: Instant,
)

@Schema(description = "약관 동의 요청")
data class AgreeRequest(
    @Schema(
        description = "동의할 약관 버전 ID 목록",
        example = "[1, 2, 3]",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val termsVersionIds: List<Long>,

    @Schema(
        description = "동의 컨텍스트",
        example = "SIGNUP",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val contextType: ConsentContextType,

    @Schema(
        description = "동의 시점의 연령대",
        example = "ADULT",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val ageGroup: AgeGroup,

    @Schema(description = "multi-step signup 세션 ID (W3 KYC 흐름)")
    val signupSessionId: UUID? = null,

    @Schema(description = "polymorphic context ID (계약 등)")
    val contextId: Long? = null,
)

@Schema(description = "약관 동의 응답")
data class ConsentResponse(
    @Schema(description = "동의 기록 ID", example = "1")
    val id: Long,

    @Schema(description = "동의한 약관 버전 ID", example = "1")
    val termsVersionId: Long,

    @Schema(description = "동의 시각", example = "2026-05-07T14:30:00Z")
    val agreedAt: Instant,
)
