package com.trana.contract.adapter.openai

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * OpenAI Chat Completions API 응답 DTO.
 *
 * 핵심 필드:
 * - choices[0].message.content: 추출 JSON (string) — ObjectMapper 로 ExtractedPrefill 로 파싱
 * - choices[0].message.refusal: 모델이 응답 거부한 사유 (AiResponseInvalid 매핑)
 * - usage: prompt/completion/total tokens (audit + 비용)
 *
 * 무시: id, object, created, system_fingerprint, logprobs, service_tier 등 → ignoreUnknown
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAiChatResponse(
    val model: String,
    val choices: List<OpenAiChoice>,
    val usage: OpenAiUsage,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAiChoice(
    val index: Int,
    val message: OpenAiResponseMessage,
    @JsonProperty("finish_reason")
    val finishReason: String?,
)

/**
 * 응답 message.
 *
 * - content / refusal 둘 중 하나만 채워짐
 *   · 정상 응답: content (JSON string), refusal = null
 *   · 거부 응답: refusal (사유), content = null
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAiResponseMessage(
    val role: String,
    val content: String? = null,
    val refusal: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAiUsage(
    @JsonProperty("prompt_tokens")
    val promptTokens: Int,
    @JsonProperty("completion_tokens")
    val completionTokens: Int,
    @JsonProperty("total_tokens")
    val totalTokens: Int,
)

/**
 * gpt-4o-mini 가 반환한 추출 JSON 의 schema 매핑.
 *
 * - OpenAiResponseMessage.content (JSON string) → ObjectMapper 로 이 객체로 파싱
 * - OpenAiSchemas.AI_EXTRACTION_SCHEMA_V1 와 1:1 매핑
 * - strict: true 가 모든 required 필드를 강제하므로 location 만 nullable
 *
 * Adapter 가 OpenAiVisionAdapter.extract() 의 일부로 반환하여
 * Service 는 raw JSON 과 ExtractedPrefill 둘 다 받아 row INSERT.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ExtractedPrefill(
    @JsonProperty("product_name")
    val productName: String,
    val price: Long,
    @JsonProperty("condition_summary")
    val conditionSummary: String,
    @JsonProperty("condition_details")
    val conditionDetails: String,
)
