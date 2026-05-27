package com.trana.contract.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.contract.ContractExamples
import com.trana.contract.dto.ContractListItem
import com.trana.contract.dto.ContractResponse
import com.trana.contract.dto.ContractStatusLogResponse
import com.trana.contract.dto.CreateContractDraftRequest
import com.trana.contract.dto.UpdateContractDraftRequest
import com.trana.contract.entity.ContractStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus

@Tag(name = "Contract Draft", description = "전자계약 DRAFT 단계 (생성/조회/수정/삭제/목록)")
@SecurityRequirement(name = "bearerAuth")
interface ContractDraftApi {
    @Operation(
        operationId = "contractCreateDraft",
        summary = "계약 DRAFT 생성",
        description = """
본인을 SELLER 또는 BUYER 로 등록하면서 빈 DRAFT 계약을 생성합니다.

흐름:
- JWT 인증 필요
- consentType 은 user.ageGroup 으로 자동 결정 (ADULT → NOT_APPLICABLE, MINOR → GUARDIAN_REQUIRED)
- 응답 publicCode 가 이후 모든 sub-endpoint 의 경로 파라미터

주의:
- title/price 등 본문은 빈 상태로 생성 → PATCH 로 채우거나 AI 추출 호출
- 반대편 party 는 W5 (서명 요청) 단계에서 매핑
              """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "DRAFT 생성 성공",
                content = [
                    Content(
                        schema = Schema(implementation = ContractResponse::class),
                        examples = [ExampleObject(name = "created", value = ContractExamples.DRAFT_CREATE_RESPONSE)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "요청 본문 validation 실패",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "JWT 인증 필요",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createDraft(
        userId: Long,
        @RequestBody @Valid request: CreateContractDraftRequest,
    ): ContractResponse

    @Operation(
        operationId = "contractGetDetail",
        summary = "계약 단건 조회",
        description = "본인이 작성한 계약만 조회 가능 (소유자가 아니면 403). soft-delete 된 계약은 404.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [
                    Content(
                        schema = Schema(implementation = ContractResponse::class),
                        examples = [ExampleObject(name = "detail", value = ContractExamples.DETAIL_RESPONSE)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "본인 작성 계약 아님",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "notOwner", value = ContractExamples.NOT_OWNER)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "존재하지 않거나 삭제됨",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "notFound", value = ContractExamples.NOT_FOUND)],
                    ),
                ],
            ),
        ],
    )
    @GetMapping("/{publicCode}")
    fun getDetail(
        userId: Long,
        @PathVariable publicCode: String,
    ): ContractResponse

    @Operation(
        operationId = "contractUpdateDraft",
        summary = "계약 DRAFT 부분 수정",
        description = """
DRAFT 상태에서만 수정 가능. null 인 필드는 변경 없음 (PATCH semantics).

덮어쓰기 주의:
- AI 추출 호출 후 자동 반영된 prefill (title/price/conditionSummary/conditionDetails) 은 PATCH 로 사용자 수정 가능
- 반대로 PATCH 후 AI 재추출 호출은 prefill 4필드를 다시 덮어씀
              """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "수정 성공",
                content = [
                    Content(
                        schema = Schema(implementation = ContractResponse::class),
                        examples = [ExampleObject(name = "detail", value = ContractExamples.DETAIL_RESPONSE)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "본인 작성 계약 아님",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "notOwner", value = ContractExamples.NOT_OWNER)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "존재하지 않거나 삭제됨",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "notFound", value = ContractExamples.NOT_FOUND)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "DRAFT 상태가 아님",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "notDraft", value = ContractExamples.NOT_DRAFT)],
                    ),
                ],
            ),
        ],
    )
    @PatchMapping("/{publicCode}")
    fun updateDraft(
        userId: Long,
        @PathVariable publicCode: String,
        @RequestBody @Valid request: UpdateContractDraftRequest,
    ): ContractResponse

    @Operation(
        operationId = "contractDeleteDraft",
        summary = "계약 DRAFT soft-delete",
        description = "DRAFT 만 삭제 가능. soft delete (deleted_at 만 채움) — audit/법적 증거는 보존.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "삭제 성공 (응답 본문 없음)"),
            ApiResponse(
                responseCode = "409",
                description = "DRAFT 상태가 아님",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "notDraft", value = ContractExamples.NOT_DRAFT)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "410",
                description = "이미 삭제됨",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "deleted", value = ContractExamples.ALREADY_DELETED)],
                    ),
                ],
            ),
        ],
    )
    @DeleteMapping("/{publicCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDraft(
        userId: Long,
        @PathVariable publicCode: String,
    )

    @Operation(
        operationId = "contractListMine",
        summary = "본인 계약 목록",
        description = """
본인이 creator 인 계약 목록 (soft-delete 제외, updated_at DESC).
status 파라미터로 필터링 가능 (생략 시 전체).
              """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "목록 조회 성공",
                content = [
                    Content(
                        array =
                            ArraySchema(
                                schema = Schema(implementation = ContractListItem::class),
                            ),
                        examples = [ExampleObject(name = "list", value = ContractExamples.LIST_RESPONSE)],
                    ),
                ],
            ),
        ],
    )
    @GetMapping
    fun listMine(
        userId: Long,
        @Parameter(description = "상태 필터 (DRAFT / SIGN_REQUESTED / SIGNED / COMPLETED 등). 생략 시 전체")
        @RequestParam(required = false) status: ContractStatus?,
    ): List<ContractListItem>

    @Operation(
        operationId = "contractMarkReady",
        summary = "DRAFT → READY 전이",
        description = """
계약 작성을 완료하고 READY 상태로 전환합니다.

조건 (서버 검증):
- DRAFT 상태여야 함 (이미 READY 면 409)
- title / price / conditionSummary / conditionDetails 모두 채워져 있어야 함 (누락 시 400)
- GUARDIAN_REQUIRED 면 guardianConsentAt 채워져 있어야 함 (미완료 시 409)

효과:
- contracts.status = READY
- contract_status_logs 에 (DRAFT → READY) row INSERT (WORM audit)

후속:
- SIGN_REQUESTED 진입 (수신자 초대) 은 W5/W6
- 다시 본문 수정하려면 /revert 로 DRAFT 되돌리기
                """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "READY 전이 성공",
                content = [
                    Content(
                        schema = Schema(implementation = ContractResponse::class),
                        examples = [ExampleObject(name = "ready", value = ContractExamples.READY_RESPONSE)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "필수 필드 누락",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "notReady", value = ContractExamples.NOT_READY_ELIGIBLE)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "DRAFT 가 아님 또는 보호자 동의 미완료",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "notDraft", value = ContractExamples.NOT_DRAFT),
                            ExampleObject(
                                name = "guardianRequired",
                                value = ContractExamples.GUARDIAN_CONSENT_REQUIRED,
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/{publicCode}/ready")
    fun markReady(
        userId: Long,
        @PathVariable publicCode: String,
    ): ContractResponse

    @Operation(
        operationId = "contractRevertToDraft",
        summary = "READY → DRAFT 되돌림",
        description = """
READY 상태의 계약을 다시 DRAFT 로 되돌립니다 (본인이 수정 재개).

조건:
- READY 상태여야 함 (DRAFT 면 409, SIGN_REQUESTED 이상이면 409 — 서명 단계 진입 후 본문 수정 차단)

효과:
- contracts.status = DRAFT
- contract_status_logs 에 (READY → DRAFT) row INSERT
                """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "DRAFT 되돌림 성공",
                content = [
                    Content(
                        schema = Schema(implementation = ContractResponse::class),
                        examples = [ExampleObject(name = "detail", value = ContractExamples.DETAIL_RESPONSE)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "READY 상태가 아님",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "notReady", value = ContractExamples.NOT_IN_READY_STATE)],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/{publicCode}/revert")
    fun revertToDraft(
        userId: Long,
        @PathVariable publicCode: String,
    ): ContractResponse

    @Operation(
        operationId = "contractStatusLogs",
        summary = "상태 전이 로그 (WORM audit)",
        description = """
본 계약의 모든 상태 전이 이력 — 시간순 정렬.

활용:
- 분쟁 시 "언제 누가 어떤 상태로 바꿨는지" 추적
- 첫 row 는 항상 INITIAL (fromStatus=null, toStatus=DRAFT)
                """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "전이 로그 조회 성공",
                content = [
                    Content(
                        array =
                            ArraySchema(
                                schema = Schema(implementation = ContractStatusLogResponse::class),
                            ),
                        examples = [ExampleObject(name = "logs", value = ContractExamples.STATUS_LOGS_RESPONSE)],
                    ),
                ],
            ),
        ],
    )
    @GetMapping("/{publicCode}/status-logs")
    fun statusLogs(
        userId: Long,
        @PathVariable publicCode: String,
    ): List<ContractStatusLogResponse>
}
