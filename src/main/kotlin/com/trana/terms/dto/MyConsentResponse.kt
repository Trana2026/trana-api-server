package com.trana.terms.dto

import com.trana.terms.entity.TermsType
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "마이페이지 — 본인의 가입 약관 동의 내역 단일 row")
data class MyConsentResponse(
    @Schema(description = "약관 버전 ID. 본문 조회는 GET /v1/terms/{type} 활용", example = "5")
    val termsId: Long,
    @Schema(description = "약관 종류", example = "SERVICE")
    val type: TermsType,
    @Schema(description = "약관 버전", example = "1.0.0")
    val version: String,
    @Schema(description = "약관 제목", example = "서비스 이용약관")
    val title: String,
    @Schema(description = "동의 시각 (UTC)")
    val agreedAt: Instant,
)
