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
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@Tag(
    name = "Contract AI Extraction",
    description = "gpt-4o-mini Vision 으로 첨부 1~2장에서 prefill 자동 추출 (비동기)",
)
@SecurityRequirement(name = "bearerAuth")
interface ContractAiApi {
    @Operation(
        operationId = "contractAiSubmit",
        summary = "AI prefill 추출 요청 (비동기)",
        description = """
사용자 동의(consentedAt) + 1~2장 첨부로 AI 추출 요청을 비동기 큐에 제출.

흐름:
1. 본 endpoint 호출 → 즉시 202 + extractionId + status=PENDING
2. 백그라운드에서 OpenAI Vision 호출 (~7초)
3. 완료 시: status=SUCCESS, Contract.title/price/conditionSummary/conditionDetails 자동 반영
4. 실패 시: status=FAILED, errorMessage 채움

폴링:
- GET /latest 또는 GET /{extractionId} 로 status 확인
- 권장: 2초 간격, 30초 timeout

제약:
- DRAFT 상태에서만 호출 가능
- attachmentIds 는 1~2개 (3장 이상은 400)
- attachmentIds 는 본 계약 소속만 (cross-contract 404)

재호출:
- 매번 새 row INSERT (audit 5년 보존)
- 이전 PENDING 이 끝나기 전 새 요청도 허용 (각 요청은 독립적인 row)
- prefill 자동 반영은 SUCCESS 시점 → 가장 늦게 끝난 SUCCESS 가 최종 반영
                """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "202",
                description = "추출 요청 등록 (status=PENDING). 폴링으로 결과 확인",
                content = [
                    Content(
                        schema = Schema(implementation = AiExtractionResponse::class),
                        examples = [ExampleObject(name = "pending", value = ContractExamples.AI_EXTRACT_PENDING)],
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
        ],
    )
    @PostMapping
    fun extract(
        userId: Long,
        @PathVariable publicCode: String,
        @RequestBody @Valid request: ExtractPrefillRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<AiExtractionResponse>

    @Operation(
        operationId = "contractAiLatest",
        summary = "가장 최근 AI 추출 결과 (status 무관)",
        description = """
본 계약의 가장 최근 row 1건 — status 와 관계없이 (PENDING/SUCCESS/FAILED).

- 200: 결과 있음 (status 별 nullable 필드 참조)
- 204: 한 번도 추출하지 않음

활용:
- 페이지 진입 시 직전 추출 상태 복원 (status=SUCCESS 면 prefill 표시, PENDING 이면 폴링 재개)
                """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "최근 추출 결과 (status 별 다른 shape)",
                content = [
                    Content(
                        schema = Schema(implementation = AiExtractionResponse::class),
                        examples = [
                            ExampleObject(name = "success", value = ContractExamples.AI_EXTRACT_SUCCESS),
                            ExampleObject(name = "pending", value = ContractExamples.AI_EXTRACT_PENDING),
                            ExampleObject(name = "failed", value = ContractExamples.AI_EXTRACT_FAILED),
                        ],
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

    @Operation(
        operationId = "contractAiGetById",
        summary = "특정 extractionId 폴링",
        description = """
submit 응답으로 받은 extractionId 로 status 확인.

- 200: row 존재 (status 별 다른 shape)
- 404: 본 계약 소속이 아닌 id (보안 차단)

활용:
- submit → extractionId 받음 → 2초 간격 폴링 → status=SUCCESS/FAILED 도달 시 중단
                """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "추출 row 1건",
                content = [
                    Content(
                        schema = Schema(implementation = AiExtractionResponse::class),
                        examples = [
                            ExampleObject(name = "pending", value = ContractExamples.AI_EXTRACT_PENDING),
                            ExampleObject(name = "success", value = ContractExamples.AI_EXTRACT_SUCCESS),
                            ExampleObject(name = "failed", value = ContractExamples.AI_EXTRACT_FAILED),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "본 계약 소속이 아닌 extractionId",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @GetMapping("/{extractionId:[0-9]+}")
    fun getById(
        userId: Long,
        @PathVariable publicCode: String,
        @PathVariable extractionId: Long,
    ): ResponseEntity<AiExtractionResponse>
}
