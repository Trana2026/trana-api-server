package com.trana.terms.dto

import com.trana.terms.entity.TermsType
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

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
    @Schema(description = "약관 본문 URL")
    val contentUrl: String,
    @Schema(description = "시행 시각 (UTC)", example = "2026-05-01T00:00:00Z")
    val effectiveAt: Instant,
)
