package com.trana.dispute.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.dispute.DisputeExamples
import com.trana.dispute.dto.DisputeListResponse
import com.trana.dispute.dto.DisputeReportRequest
import com.trana.dispute.dto.DisputeResponse
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
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody

@Tag(name = "Contract Dispute", description = "계약 신고 (W7) — 접수 / 본인 취소 / 조회")
interface DisputeApi {
    @Operation(
        operationId = "disputeReport",
        summary = "신고 접수",
        description = """
계약 참여자(creator OR party)가 SIGNED 또는 COMPLETED 단계 계약에 대해 신고 접수.

흐름:
- 본인이 이미 활성(REPORTED) 신고 1건 있으면 409 (취소 후 재신고 가능)
- 첫 신고면 contract.dispute_state = REPORTED 로 전이
- 다중 신고 시 dispute_records row 만 추가, contract.dispute_state 는 REPORTED 유지
- 알림톡 5번 (피신고자에게 발송)
              """,
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "신고 접수 완료",
                content = [
                    Content(
                        schema = Schema(implementation = DisputeResponse::class),
                        examples = [ExampleObject(name = "reported", value = DisputeExamples.REPORTED_RESPONSE)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "계약 참여자 아님",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "notAccessible", value = DisputeExamples.NOT_ACCESSIBLE)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "신고 가능 상태 아님 또는 이미 활성 신고 보유",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "notReportable", value = DisputeExamples.NOT_REPORTABLE),
                            ExampleObject(name = "alreadyActive", value = DisputeExamples.ALREADY_ACTIVE),
                        ],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/{publicCode}/disputes")
    fun report(
        userId: Long,
        @PathVariable publicCode: String,
        @RequestBody @Valid request: DisputeReportRequest,
        httpRequest: HttpServletRequest,
    ): DisputeResponse

    @Operation(
        operationId = "disputeCancelByReporter",
        summary = "신고자 본인 취소",
        description = """
본인이 접수한 활성(REPORTED) 신고만 취소 가능. 취소 후 같은 계약에 재신고 가능.

흐름:
- 다른 사람 신고 / 이미 취소된 신고 / 존재하지 않는 신고 모두 404 (id enumeration 방지)
- 활성 신고가 0건이 되면 contract.dispute_state = NONE 복원
              """,
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "취소 완료"),
            ApiResponse(
                responseCode = "404",
                description = "신고 없음 또는 본인 신고 아님 또는 이미 취소됨",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "notFound", value = DisputeExamples.NOT_FOUND)],
                    ),
                ],
            ),
        ],
    )
    @DeleteMapping("/{publicCode}/disputes/{disputeId}")
    fun cancelByReporter(
        userId: Long,
        @PathVariable publicCode: String,
        @PathVariable disputeId: Long,
    )

    @Operation(
        operationId = "disputeList",
        summary = "계약 단위 신고 목록 (양측 조회)",
        description = """
계약 참여자(creator OR party) 가 자기 계약의 신고 목록 조회.
신고자 user_id 는 응답에 노출 X — isMine boolean 으로 본인 신고 여부만 표시 (피신고자 보복 방지).
              """,
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "신고 목록 (최신순)",
                content = [
                    Content(
                        schema = Schema(implementation = DisputeListResponse::class),
                        examples = [ExampleObject(name = "list", value = DisputeExamples.LIST_RESPONSE)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "계약 참여자 아님",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "notAccessible", value = DisputeExamples.NOT_ACCESSIBLE)],
                    ),
                ],
            ),
        ],
    )
    @GetMapping("/{publicCode}/disputes")
    fun list(
        userId: Long,
        @PathVariable publicCode: String,
    ): DisputeListResponse

    @Operation(
        operationId = "disputeEvidencePackage",
        summary = "증거 패키지 다운로드 (zip)",
        description = """
신고자 본인이 자기 계약의 증거 패키지(계약 PDF + 첨부 이미지) 를 zip 으로 다운로드.

구성:
- contract.pdf (v3 최종본)
- attachments/01.jpg, 02.jpg ... (등록된 순서대로)

권한:
- 계약 참여자 + 본인이 활성(REPORTED) 신고 1건 이상 보유
- 신고 취소 후 다운로드 X — 재신고하면 다시 가능
          """,
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "zip 파일 (application/zip)",
                content = [
                    Content(
                        mediaType = "application/zip",
                        schema = Schema(type = "string", format = "binary"),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "계약 참여자 아님 또는 활성 신고 없음",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "notAccessible", value = DisputeExamples.NOT_ACCESSIBLE),
                            ExampleObject(name = "noActiveReport", value = DisputeExamples.NO_ACTIVE_REPORT),
                        ],
                    ),
                ],
            ),
        ],
    )
    @GetMapping("/{publicCode}/evidence-package")
    fun evidencePackage(
        userId: Long,
        @PathVariable publicCode: String,
    ): ResponseEntity<StreamingResponseBody>
}
