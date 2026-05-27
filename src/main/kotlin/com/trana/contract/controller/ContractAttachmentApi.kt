package com.trana.contract.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.contract.ContractExamples
import com.trana.contract.dto.AttachmentResponse
import com.trana.contract.dto.PresignAttachmentRequest
import com.trana.contract.dto.PresignAttachmentResponse
import com.trana.contract.dto.RegisterAttachmentRequest
import io.swagger.v3.oas.annotations.Operation
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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus

@Tag(name = "Contract Attachment", description = "계약 첨부 사진 (presign → upload → register → list/delete)")
@SecurityRequirement(name = "bearerAuth")
interface ContractAttachmentApi {
    @Operation(
        operationId = "contractAttachmentPresign",
        summary = "1단계 — presigned PUT URL 발급",
        description = """
클라이언트가 S3 로 직접 PUT 할 수 있는 presigned URL 발급.

흐름:
- 호출 시점에 capacity 검사 (이미 7장이면 409)
- 응답 uploadUrl 로 클라이언트가 S3 PUT (Content-Type 헤더 일치 필수)
- PUT 완료 후 2단계 register 호출

주의:
- URL TTL 10분 — 만료 후 PUT 은 403
- contentType 은 image/jpeg, image/png, image/webp, image/heic, image/heif 만 허용
- 동시에 여러 presign 발급 가능 (capacity 는 register 시 다시 확인)
              """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "presign 발급 성공",
                content = [
                    Content(
                        schema = Schema(implementation = PresignAttachmentResponse::class),
                        examples = [
                            ExampleObject(name = "presigned", value = ContractExamples.ATTACHMENT_PRESIGN_RESPONSE),
                        ],
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
                responseCode = "409",
                description = "DRAFT 아님 또는 7장 초과",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "notDraft", value = ContractExamples.NOT_DRAFT),
                            ExampleObject(name = "maxAttachments", value = ContractExamples.MAX_ATTACHMENTS),
                        ],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/presign")
    fun presign(
        userId: Long,
        @PathVariable publicCode: String,
        @RequestBody @Valid request: PresignAttachmentRequest,
    ): PresignAttachmentResponse

    @Operation(
        operationId = "contractAttachmentRegister",
        summary = "2단계 — 업로드 완료 후 메타 등록",
        description = """
S3 PUT 완료 후 호출. sortOrder 는 등록 시점 count 로 자동 부여 (0-based).

보안:
- s3Key 는 presign 응답값 그대로 — Service 가 발급한 key 외에는 정상 흐름에서 등장하지 않음
- 클라이언트가 임의 s3Key 를 register 해도 capacity/owner 검증은 통과하지만, 실제 객체가 S3 에 없으면 후속 AI/계약 진행에서 실패
              """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "등록 성공",
                content = [
                    Content(
                        schema = Schema(implementation = AttachmentResponse::class),
                        examples = [
                            ExampleObject(name = "registered", value = ContractExamples.ATTACHMENT_REGISTER_RESPONSE),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "DRAFT 아님 또는 7장 초과 (race 대비)",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "maxAttachments", value = ContractExamples.MAX_ATTACHMENTS),
                        ],
                    ),
                ],
            ),
        ],
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun register(
        userId: Long,
        @PathVariable publicCode: String,
        @RequestBody @Valid request: RegisterAttachmentRequest,
    ): AttachmentResponse

    @Operation(
        operationId = "contractAttachmentList",
        summary = "첨부 목록 조회",
        description = "본인 계약의 첨부 목록 (sort_order asc). 상태 무관 — DRAFT/SIGNED 등 모두 조회 가능.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [
                    Content(
                        array = ArraySchema(schema = Schema(implementation = AttachmentResponse::class)),
                        examples = [ExampleObject(name = "list", value = ContractExamples.ATTACHMENT_LIST_RESPONSE)],
                    ),
                ],
            ),
        ],
    )
    @GetMapping
    fun list(
        userId: Long,
        @PathVariable publicCode: String,
    ): List<AttachmentResponse>

    @Operation(
        operationId = "contractAttachmentDelete",
        summary = "첨부 단건 삭제",
        description = """
DRAFT 상태에서만 삭제 가능. DB row 삭제 후 S3 객체 삭제 (실패 시 tx 롤백 — 일관성 유지).

주의:
- sort_order 재정렬 안 함 (hole 발생, UI 는 asc 정렬로 흡수)
- 다른 계약의 attachmentId 를 보내면 404 (cross-contract 차단)
              """,
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "삭제 성공 (응답 본문 없음)"),
            ApiResponse(
                responseCode = "404",
                description = "첨부 없음 또는 다른 계약 소속",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "DRAFT 아님",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "notDraft", value = ContractExamples.NOT_DRAFT)],
                    ),
                ],
            ),
        ],
    )
    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        userId: Long,
        @PathVariable publicCode: String,
        @PathVariable attachmentId: Long,
    )
}
