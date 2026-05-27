package com.trana.contract.dto

import com.trana.contract.adapter.openai.ExtractedPrefill
import com.trana.contract.adapter.openai.OpenAiUsage
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant

@Schema(description = "AI prefill 추출 요청 (1~2장 첨부 + 동의 타임스탬프)")
data class ExtractPrefillRequest(
    @field:NotEmpty
    @field:Size(min = 1, max = 2, message = "AI 분석 입력 사진은 1~2장")
    @field:Schema(description = "분석할 첨부 id 배열 (sort_order 순서대로 전달)", example = "[101, 103]")
    val attachmentIds: List<Long>,
    @field:NotNull
    @field:Schema(
        description = "사용자가 AI 처리 동의 체크박스를 누른 시각 (audit, client clock)",
        example = "2026-05-20T10:07:00Z",
    )
    val consentedAt: Instant,
)

@Schema(description = "AI prefill 추출 응답 — Contract 의 title/price/conditionSummary/conditionDetails 4필드는 자동 반영됨")
data class AiExtractionResponse(
    @field:Schema(description = "ai_extractions row id (재현/audit)", example = "9001")
    val extractionId: Long,
    @field:Schema(description = "사용된 모델 (saved entity 기반)", example = "gpt-4o-mini")
    val model: String,
    @field:Schema(description = "프롬프트 버전 (saved entity 기반)", example = "v1")
    val promptVersion: String,
    @field:Schema(description = "추출된 prefill — Contract 본문에 자동 반영됨")
    val prefill: ExtractedPrefill,
    @field:Schema(description = "OpenAI 호출 latency (ms)", example = "1842")
    val latencyMs: Long,
    @field:Schema(description = "OpenAI usage — 토큰 사용량 (prompt/completion/total)")
    val usage: OpenAiUsage,
    @field:Schema(description = "ai_extractions.extracted_at (UTC)")
    val extractedAt: Instant,
)
