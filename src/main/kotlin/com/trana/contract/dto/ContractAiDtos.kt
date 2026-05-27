package com.trana.contract.dto

import com.trana.contract.adapter.openai.ExtractedPrefill
import com.trana.contract.adapter.openai.OpenAiUsage
import com.trana.contract.entity.ExtractionStatus
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

@Schema(
    description = """
AI prefill 추출 상태 응답 (비동기).

- POST /extract: 즉시 202 + status=PENDING 반환
- GET /latest 또는 GET /{extractionId}: 폴링용 — SUCCESS/FAILED 로 전이된 후 결과 확인

status 별 채워지는 필드:
- PENDING: prefill / latencyMs / usage / errorMessage 모두 null
- SUCCESS: prefill / latencyMs / usage 채움 + Contract 본문에 자동 반영됨
- FAILED: errorMessage 채움 (운영 디버깅, 사용자에게는 generic 메시지 권장)
  """,
)
data class AiExtractionResponse(
    @field:Schema(description = "ai_extractions row id (재현/audit/폴링 키)", example = "9001")
    val extractionId: Long,
    @field:Schema(description = "추출 상태", example = "PENDING")
    val status: ExtractionStatus,
    @field:Schema(description = "사용된 모델", example = "gpt-4o-mini")
    val model: String,
    @field:Schema(description = "프롬프트 버전", example = "v1")
    val promptVersion: String,
    @field:Schema(description = "추출된 prefill — SUCCESS 시만. Contract 본문 자동 반영됨")
    val prefill: ExtractedPrefill?,
    @field:Schema(description = "OpenAI 호출 latency (ms) — SUCCESS 시만", example = "7172")
    val latencyMs: Long?,
    @field:Schema(description = "OpenAI usage — SUCCESS 시만")
    val usage: OpenAiUsage?,
    @field:Schema(description = "실패 사유 — FAILED 시만 (운영 디버깅)")
    val errorMessage: String?,
    @field:Schema(description = "요청 등록 시각 — ai_extractions.extracted_at (UTC)")
    val extractedAt: Instant,
)
