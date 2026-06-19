package com.trana.user.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

@Schema(description = "1:1 문의 작성 요청")
data class CreateInquiryRequest(
    @field:NotBlank
    @field:Email
    @Schema(description = "회신받을 이메일 — 운영자가 직접 이 주소로 답변 전송", example = "user@example.com")
    val email: String,
    @field:NotBlank
    @field:Size(min = 1, max = 100)
    @Schema(description = "문의 제목 (1~100자)", example = "결제 오류 문의")
    val title: String,
    @field:NotBlank
    @field:Size(min = 1, max = 2000)
    @Schema(description = "문의 내용 (1~2000자)", example = "거래 진행 중 다음 오류가 발생했습니다...")
    val content: String,
)

@Schema(description = "1:1 문의 목록 — 단일 row")
data class InquirySummaryResponse(
    @Schema(description = "문의 식별자 (nanoid 12자) — 상세 조회 시 사용", example = "Iq7sK2x9Pq3R")
    val publicCode: String,
    @Schema(description = "문의 제목", example = "결제 오류 문의")
    val title: String,
    @Schema(description = "작성 시각 (UTC)")
    val createdAt: Instant,
)

@Schema(description = "1:1 문의 상세")
data class InquiryDetailResponse(
    @Schema(description = "문의 식별자", example = "Iq7sK2x9Pq3R")
    val publicCode: String,
    @Schema(description = "회신 이메일", example = "user@example.com")
    val email: String,
    @Schema(description = "문의 제목", example = "결제 오류 문의")
    val title: String,
    @Schema(description = "문의 내용")
    val content: String,
    @Schema(description = "작성 시각 (UTC)")
    val createdAt: Instant,
)
