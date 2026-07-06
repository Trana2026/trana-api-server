package com.trana.contract.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.contract.ContractCancellationExamples
import com.trana.contract.dto.CancellationRequestRequest
import com.trana.contract.dto.CancellationRequestResponse
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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@Tag(name = "Contract Cancellation", description = "계약 취소 요청 (W7) — 서명 요청 받은 측이 요청 / 상대 확정 / 요청자 revoke / 활성 요청 조회")
interface ContractCancellationApi {
    @Operation(
        operationId = "contractCancellationRequest",
        summary = "취소 요청 접수",
        description = """
서명 요청을 받은 측이 취소 요청. 가능 시점:
- SHARED (1차 서명 수신자 = receiver)
- RECEIVER_SIGNED (최종 서명 수신자 = creator)

흐름:
- 송신 측 (서명 요청 보낸 사람) 시도 시 403 NotEligibleRequester
- 활성 요청 1건 이미 보유 시 409 AlreadyActive
- 성공 시 contract.status → CANCEL_REQUESTED 전이
- 알림톡 6번 → 상대 측 (A'-7)
              """,
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "요청 접수 완료",
                content = [
                    Content(
                        schema = Schema(implementation = CancellationRequestResponse::class),
                        examples = [
                            ExampleObject(
                                name = "requested",
                                value = ContractCancellationExamples.REQUESTED_RESPONSE,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "계약 참여자 아님 또는 송신 측 시도",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "notAccessible", value = ContractCancellationExamples.NOT_ACCESSIBLE),
                            ExampleObject(
                                name = "notEligibleRequester",
                                value = ContractCancellationExamples.NOT_ELIGIBLE_REQUESTER,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "취소 요청 가능 상태 아님 또는 이미 활성 요청 보유",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(
                                name = "notRequestable",
                                value = ContractCancellationExamples.NOT_REQUESTABLE,
                            ),
                            ExampleObject(name = "alreadyActive", value = ContractCancellationExamples.ALREADY_ACTIVE),
                        ],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/{publicCode}/cancellation-requests")
    fun request(
        userId: Long,
        @PathVariable publicCode: String,
        @RequestBody @Valid request: CancellationRequestRequest,
        httpRequest: HttpServletRequest,
    ): CancellationRequestResponse

    @Operation(
        operationId = "contractCancellationConfirm",
        summary = "상대 측 취소 확정",
        description = """
요청자가 아닌 측이 취소 확정 → contract.status → CANCELLED 전이.

흐름:
- 활성 요청 없으면 404
- 요청자 본인이 시도 시 403 SelfConfirm
- 성공 시 listMine 응답에서 해당 계약 제외 (스펙: "계약 목록에서 삭제")
              """,
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "확정 완료"),
            ApiResponse(
                responseCode = "403",
                description = "계약 참여자 아님 또는 요청자 본인 시도",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "notAccessible", value = ContractCancellationExamples.NOT_ACCESSIBLE),
                            ExampleObject(name = "selfConfirm", value = ContractCancellationExamples.SELF_CONFIRM),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "활성 취소 요청 없음",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "notFound", value = ContractCancellationExamples.NOT_FOUND)],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/{publicCode}/cancellation-requests/confirm")
    fun confirm(
        userId: Long,
        @PathVariable publicCode: String,
    )

    @Operation(
        operationId = "contractCancellationRevoke",
        summary = "취소 요청 취소 (요청자 본인)",
        description = """
요청자 본인이 자기 취소 요청을 되돌림.

흐름:
- 활성 요청 없으면 404 NotFound
- 요청자 본인 아닌 사람이 시도 시 403 NotEligibleRequester
- 성공 시 contract.status → previousStatus 복구 (SHARED 또는 RECEIVER_SIGNED), request.status → REVOKED (audit)
                """,
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "revoke 완료"),
            ApiResponse(
                responseCode = "403",
                description = "계약 참여자 아님 또는 요청자 아님",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "notAccessible", value = ContractCancellationExamples.NOT_ACCESSIBLE),
                            ExampleObject(
                                name = "notEligibleRequester",
                                value = ContractCancellationExamples.NOT_ELIGIBLE_REQUESTER,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "활성 취소 요청 없음",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "notFound", value = ContractCancellationExamples.NOT_FOUND)],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/{publicCode}/cancellation-requests/revoke")
    fun revoke(
        userId: Long,
        @PathVariable publicCode: String,
    )

    @Operation(
        operationId = "contractCancellationActive",
        summary = "활성 취소 요청 조회 (양측)",
        description = """
계약 참여자 (creator OR party) 가 자기 계약의 활성(REQUESTED) 취소 요청 조회.
- 활성 요청 없으면 204 No Content
- 요청자 user_id 미노출 — isMine boolean 만
- 스펙의 "취소 내용 확인 가능한 바텀시트" 데이터 source
              """,
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "활성 취소 요청",
                content = [
                    Content(
                        schema = Schema(implementation = CancellationRequestResponse::class),
                        examples = [
                            ExampleObject(
                                name = "active",
                                value = ContractCancellationExamples.ACTIVE_RESPONSE,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(responseCode = "204", description = "활성 취소 요청 없음"),
            ApiResponse(
                responseCode = "403",
                description = "계약 참여자 아님",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(
                                name = "notAccessible",
                                value = ContractCancellationExamples.NOT_ACCESSIBLE,
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @GetMapping("/{publicCode}/cancellation-request")
    fun getActive(
        userId: Long,
        @PathVariable publicCode: String,
    ): CancellationRequestResponse?
}
