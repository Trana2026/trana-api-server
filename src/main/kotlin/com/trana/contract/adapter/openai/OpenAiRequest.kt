package com.trana.contract.adapter.openai

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * OpenAI Chat Completions API 요청 DTO.
 *
 * - response_format = json_schema (strict: true) → 응답 schema 100% 강제
 * - vision 입력: user message 의 content 가 array (text + image_url 혼합)
 * - content 를 List<ContentPart> 로 통일 (system 도 단일 Text part) — 직렬화 일관성
 */
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    @JsonProperty("response_format")
    val responseFormat: OpenAiResponseFormat,
    @JsonProperty("max_tokens")
    val maxTokens: Int? = null,
)

data class OpenAiMessage(
    val role: String,
    val content: List<OpenAiContentPart>,
)

/**
 * content array 의 원소.
 *
 * Jackson type discriminator: type 필드 ("text" | "image_url")
 * - direction: 직렬화만 (응답 파싱은 별개 DTO 사용)
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = OpenAiContentPart.Text::class, name = "text"),
    JsonSubTypes.Type(value = OpenAiContentPart.ImageUrl::class, name = "image_url"),
)
sealed class OpenAiContentPart {
    data class Text(
        val text: String,
    ) : OpenAiContentPart()

    data class ImageUrl(
        @JsonProperty("image_url")
        val imageUrl: ImageUrlPayload,
    ) : OpenAiContentPart()
}

/**
 * image_url payload.
 *
 * - url: presigned GET URL (TTL 5분)
 * - detail: "low" | "high" | "auto" (null = auto)
 *   · 게시글 스크린샷의 텍스트 가독성을 위해 "high" 권장 (Service 단계 결정)
 *   · "low" 는 비용 절감용 / "high" 는 토큰 + 비용 증가
 */
data class ImageUrlPayload(
    val url: String,
    val detail: String? = null,
)

/**
 * response_format = json_schema (strict: true).
 *
 * - name: schema 식별자 (gpt-4o-mini 가 응답 형식 정체성 인지)
 * - strict: true 면 schema 100% 강제 (additionalProperties: false / 모든 필드 required 필수)
 * - schema: Map<String, Any> — Jackson 으로 JSON 직렬화 (OpenAiSchemas 가 상수 보유)
 */
data class OpenAiResponseFormat(
    val type: String = "json_schema",
    @JsonProperty("json_schema")
    val jsonSchema: OpenAiJsonSchema,
)

data class OpenAiJsonSchema(
    val name: String,
    val strict: Boolean = true,
    val schema: Map<String, Any>,
)
