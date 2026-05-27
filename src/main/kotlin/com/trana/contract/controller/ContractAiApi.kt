package com.trana.contract.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.contract.ContractExamples
import com.trana.contract.dto.AiExtractionResponse
import com.trana.contract.dto.ExtractPrefillRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@Tag(name = "Contract AI Extraction", description = "gpt-4o-mini Vision 으로 첨부 1~2장에서 prefill 자동 추출")
@SecurityRequirement(name = "bearerAuth")
interface ContractAiApi {
    @Operation(
        operationId = "contractAiExtract",
        summary = "AI prefill 추출 실행",
        description = """
사용자 동의(consentedAt) + 1~2장 첨부 → gpt-4o-mini Vision 호출 → prefill 4필드 자동 반영.

자동 반영:
- Contract.title / price / conditionSummary / conditionDetails 가 AI 결과로 업데이트됨
- 기존 사용자 입력값은 덮어쓰기됨 → 프론트에서 confirm dialog 권장

재호출:
- 매번 새 ai_extractions row INSERT (audit 5년 보존)
- prefill 4필드는 매번 덮어쓰기

제약:
- DRAFT 상태에서만 호출 가능
- attachmentIds 는 1~2개 (3장 이상은 400)
- attachmentIds 는 본 계약 소속만 (cross-contract 404)
              """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "추출 성공 (prefill 자동 반영됨)",
                content = [
                    Content(
                        schema = Schema(implementation = AiExtractionResponse::class),
                        examples = [ExampleObject(name = "extracted", value = ContractExamples.AI_EXTRACT_RESPONSE)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "사진 개수 위반 (1~2장)",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "imageCount", value = ContractExamples.AI_IMAGE_COUNT_INVALID),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "첨부 id 가 본 계약 소속 아님 또는 존재하지 않음",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "DRAFT 상태 아님",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "notDraft", value = ContractExamples.NOT_DRAFT)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "502",
                description = "OpenAI 호출 실패 또는 응답 파싱 실패",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "callFailed", value = ContractExamples.AI_EXTRACTION_FAILED),
                            ExampleObject(name = "parseFailed", value = ContractExamples.AI_RESPONSE_INVALID),
                        ],
                    ),
                ],
            ),
        ],
    )
    @PostMapping
    fun extract(
        userId: Long,
        @PathVariable publicCode: String,
        @RequestBody @Valid request: ExtractPrefillRequest,
    ): AiExtractionResponse

    @Operation(
        operationId = "contractAiLatest",
        summary = "가장 최근 AI 추출 결과 조회",
        description = """
본 계약의 가장 최근 AI 추출 결과 단건.

- 200: 결과 있음 (prefill 자동 반영된 값 그대로)
- 204: 한 번도 추출하지 않음 (응답 본문 없음)

활용:
- 페이지 리로드 시 마지막 추출 결과 + usage/latency 재표시
              """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "이전 추출 결과 존재",
                content = [
                    Content(
                        schema = Schema(implementation = AiExtractionResponse::class),
                        examples = [ExampleObject(name = "latest", value = ContractExamples.AI_EXTRACT_RESPONSE)],
                    ),
                ],
            ),
            ApiResponse(responseCode = "204", description = "추출 이력 없음 (응답 본문 없음)"),
        ],
    )
    @GetMapping("/latest")
    fun latest(
        userId: Long,
        @PathVariable publicCode: String,
    ): ResponseEntity<AiExtractionResponse>
}
