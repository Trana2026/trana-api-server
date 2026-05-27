package com.trana.contract.adapter.openai

import com.trana.contract.ContractException
import com.trana.contract.adapter.storage.ContractAttachmentStorage
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.ObjectMapper
import java.net.http.HttpClient
import java.time.Duration

/**
 * OpenAI Vision (Chat Completions API) 어댑터.
 *
 * - gpt-4o-mini + Structured Outputs (json_schema strict) 사용
 * - 이미지: S3 presigned GET URL 로 OpenAI 가 직접 다운로드 (base64 X — latency/메모리 이득)
 * - 1~2장 이미지 입력 → ExtractedPrefill JSON 반환
 * - latency / token usage 기록 (ai_extractions 컬럼 채움)
 * - 예외: 모든 실패는 ContractException.AiExtractionFailed 로 매핑
 */
@Component
class OpenAiVisionAdapter(
    private val attachmentStorage: ContractAttachmentStorage,
    private val props: OpenAiProperties,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient: RestClient =
        RestClient
            .builder()
            .baseUrl(props.baseUrl)
            .defaultHeader("Authorization", "Bearer ${props.apiKey}")
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .requestFactory(
                JdkClientHttpRequestFactory(
                    HttpClient
                        .newBuilder()
                        .connectTimeout(Duration.ofSeconds(props.timeoutSeconds))
                        .build(),
                ).apply {
                    setReadTimeout(Duration.ofSeconds(props.timeoutSeconds))
                },
            ).build()

    /**
     * 첨부 사진들로부터 prefill 추출.
     *
     * @param s3Keys 분석할 이미지의 S3 key (1~2개, 호출자가 사전 검증)
     */
    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    fun extractPrefill(s3Keys: List<String>): AiExtractionResult {
        require(s3Keys.isNotEmpty()) { "s3Keys must not be empty" }

        val imageUrls = s3Keys.map { attachmentStorage.presignGet(it) }
        val request = buildRequest(imageUrls)

        val started = System.currentTimeMillis()
        val response =
            try {
                restClient
                    .post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(OpenAiChatResponse::class.java)
            } catch (e: RestClientResponseException) {
                log.error("OpenAI API error status={} body={}", e.statusCode, e.responseBodyAsString)
                throw ContractException.AiExtractionFailed("OpenAI API ${e.statusCode}", e)
            } catch (e: Exception) {
                log.error("OpenAI 호출 실패", e)
                throw ContractException.AiExtractionFailed("OpenAI 호출 실패: ${e.message}", e)
            }
        val latencyMs = System.currentTimeMillis() - started

        if (response == null) {
            throw ContractException.AiExtractionFailed("OpenAI 응답이 비어있습니다")
        }

        val choice =
            response.choices.firstOrNull()
                ?: throw ContractException.AiExtractionFailed("OpenAI choices 없음")
        choice.message.refusal?.let {
            log.warn("OpenAI refusal: {}", it)
            throw ContractException.AiExtractionFailed("OpenAI 거부: $it")
        }
        val rawJson =
            choice.message.content
                ?: throw ContractException.AiExtractionFailed("OpenAI content 비어있음")

        val prefill =
            try {
                objectMapper.readValue(rawJson, ExtractedPrefill::class.java)
            } catch (e: Exception) {
                log.error("Prefill JSON parse 실패: {}", rawJson, e)
                throw ContractException.AiExtractionFailed("응답 JSON 파싱 실패", e)
            }

        return AiExtractionResult(
            prefill = prefill,
            rawJson = rawJson,
            usage = response.usage,
            latencyMs = latencyMs,
        )
    }

    private fun buildRequest(imageUrls: List<String>): OpenAiChatRequest =
        OpenAiChatRequest(
            model = props.model,
            messages =
                listOf(
                    OpenAiMessage(
                        role = "system",
                        content = listOf(OpenAiContentPart.Text(OpenAiPromptTemplates.SYSTEM_PROMPT_V1)),
                    ),
                    OpenAiMessage(
                        role = "user",
                        content =
                            buildList {
                                add(OpenAiContentPart.Text("아래 이미지에서 상품 정보를 추출해주세요."))
                                imageUrls.forEach {
                                    add(
                                        OpenAiContentPart.ImageUrl(
                                            ImageUrlPayload(url = it, detail = "high"),
                                        ),
                                    )
                                }
                            },
                    ),
                ),
            responseFormat =
                OpenAiResponseFormat(
                    jsonSchema =
                        OpenAiJsonSchema(
                            name = "contract_prefill",
                            schema = OpenAiSchemas.CONTRACT_PREFILL_SCHEMA,
                            strict = true,
                        ),
                ),
            maxTokens = 1000,
        )
}

/**
 * AI 추출 결과 (Service 가 ai_extractions 영속화 + Controller 응답).
 */
data class AiExtractionResult(
    val prefill: ExtractedPrefill,
    val rawJson: String,
    val usage: OpenAiUsage,
    val latencyMs: Long,
)
