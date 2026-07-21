package com.trana.terms.dto

import com.trana.terms.entity.TermsType
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "약관 단건 + 전문(마크다운) 응답")
data class TermsContentResponse(
    @Schema(description = "약관 ID", example = "5")
    val id: Long,
    @Schema(description = "약관 유형", example = "ELECTRONIC_SIGNATURE")
    val type: TermsType,
    @Schema(description = "약관 버전", example = "1.0")
    val version: String,
    @Schema(description = "약관 제목", example = "개인정보 제3자 제공 동의서")
    val title: String,
    @Schema(description = "본문 SHA-256 (변조 검증용)", example = "22e0171240528c82...")
    val contentHash: String,
    @Schema(description = "약관 본문 (마크다운 원문)")
    val content: String,
    @Schema(description = "시행 시각 (UTC)")
    val effectiveAt: Instant,
)
