package com.trana.contract.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.contract.ContractExamples
import com.trana.contract.dto.ContractListItem
import com.trana.contract.dto.ContractPdfDownloadResponse
import com.trana.contract.dto.ContractResponse
import com.trana.contract.dto.ContractStatusLogResponse
import com.trana.contract.dto.CreateContractDraftRequest
import com.trana.contract.dto.RequestRevisionRequest
import com.trana.contract.dto.ShareContractRequest
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
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
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
@Suppress("LargeClass", "TooManyFunctions")
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
        @Parameter(
            description =
                "상태 필터 (IN_PROGRESS / DRAFT / READY / SHARED " +
                    "/ REVISION_REQUESTED / RECEIVER_SIGNED " +
                    "/ SIGNED / COMPLETED 등). 생략 시 전체",
        )
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
- SHARED 진입 (공유하기 + 카카오톡 알림톡 발송) 은 W6
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
        operationId = "contractShare",
        summary = "READY → SHARED 전이 + 카카오톡 알림톡 발송",
        description = """
계약을 수신자에게 공유합니다 — 백엔드가 카카오톡 알림톡 1번 템플릿 (`[Trana] 새 계약서 도착`) 으로 invitation URL 자동 발송.

조건 (서버 검증):
- READY 상태여야 함 (DRAFT 또는 SHARED 이상이면 409)
- receiverName / receiverPhone 필수 (Bean Validation)

효과:
- contract_invitations row 생성 (token jnanoid 21자, TTL 7일, receiver_name/phone 저장)
- 카카오톡 알림톡 자동 발송 (현재 BSP 미준비 — Mock 으로 log 만 출력)
- contracts.status = SHARED
- contract_status_logs 에 (READY → SHARED) row INSERT (WORM audit)

후속:
- 수신자가 카톡 링크 클릭 → 가입 + 본인인증 + 서명 → RECEIVER_SIGNED (W6 진행 중)
- SHARED 이후 본문 수정 불가 (변경 필요 시 CANCELLED 후 신규 DRAFT)
                  """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "공유 성공 (status=SHARED)",
                content = [
                    Content(
                        schema = Schema(implementation = ContractResponse::class),
                        examples = [ExampleObject(name = "shared", value = ContractExamples.SHARED_RESPONSE)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "이름/phone 검증 실패",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "validation", value = ContractExamples.SHARE_VALIDATION_FAILED),
                        ],
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
    @PostMapping("/{publicCode}/share")
    fun share(
        @Parameter(hidden = true) userId: Long,
        @PathVariable publicCode: String,
        @RequestBody @Valid request: ShareContractRequest,
    ): ContractResponse

    @Operation(
        operationId = "contractRequestRevision",
        summary = "수신자 수정 요청 — SHARED → REVISION_REQUESTED",
        description = """
SHARED 상태 계약에 대해 수신자가 필드별 수정 이유 입력 → 생성자 알림톡 발송 + status REVISION_REQUESTED 전이.

조건:
- 인증된 user (수신자) — 가입+본인인증 완료 상태
- 유효 invitation token (미만료 + 미사용)
- 계약 status 가 SHARED 여야 함 (이미 REVISION_REQUESTED 또는 RECEIVER_SIGNED 면 409)
- 최소 1개 필드의 reason 필수 (Bean Validation)

효과:
- contract_revision_requests row INSERT (필드별 reason audit)
- contracts.status = REVISION_REQUESTED
- contract_status_logs (SHARED → REVISION_REQUESTED) row INSERT (WORM)
- 생성자에게 카카오톡 알림톡 4번 템플릿 `[Trana] 수정 요청 도착`

후속:
- 생성자가 알림 보고 "계약서 수정하기" 누름 → POST /v1/contracts/{publicCode}/revert (REVISION_REQUESTED → DRAFT)
- 수정 후 markReady → share 재호출 (재 알림톡)
          """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "수정 요청 성공 (status=REVISION_REQUESTED)",
                content = [
                    Content(
                        schema = Schema(implementation = ContractResponse::class),
                        examples = [
                            ExampleObject(
                                name = "revisionRequested",
                                value = ContractExamples.REVISION_REQUESTED_RESPONSE,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "reason 미입력 (최소 1개 필수)",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(
                                name = "noReason",
                                value = ContractExamples.REVISION_NO_REASON,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "초대 토큰 못 찾음",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(
                                name = "invitationNotFound",
                                value = ContractExamples.INVITATION_NOT_FOUND,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "SHARED 상태가 아님",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(
                                name = "notShared",
                                value = ContractExamples.NOT_IN_SHARED_STATE,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "410",
                description = "초대 토큰 만료/사용됨",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(
                                name = "invitationExpired",
                                value = ContractExamples.INVITATION_EXPIRED,
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/invitations/{token}/revisions")
    fun requestRevision(
        @Parameter(hidden = true) userId: Long,
        @PathVariable token: String,
        @RequestBody @Valid request: RequestRevisionRequest,
    ): ContractResponse

    @Operation(
        operationId = "contractAcceptInvitation",
        summary = "수신자 invitation 수락 (계약 당사자 연결)",
        description = """
카카오톡 알림톡 링크로 진입한 수신자가 가입/로그인 완료 후 호출 — 자신을 계약의 BUYER (또는 SELLER) 로 정식 연결합니다.

전제:
- 사용자 가입 완료 (성인: KYC SUCCESS → UserStatus.ACTIVE / 미성년: 보호자 검증 완료 → guardianVerifiedAt != null)
- contract.status = SHARED
- invitation 미사용 + 미만료

처리:
- 기존 ContractParty 매핑이 있으면 idempotent — 재호출 시 그대로 ContractResponse 반환
- 신규면 creator partyType 의 반대편 (SELLER ↔ BUYER) 으로 ContractParty INSERT + validated=true
- invitation.markUsed(userId) — 토큰 1회 소비

이후 단계:
- 수신자는 PDF v1 미리보기 → 약관 동의 + 서명 (#31) 또는 수정 요청 (#37, 이미 구현됨)

에러:
- 404 CONTRACT_INVITATION_NOT_FOUND : 토큰 없음
- 410 CONTRACT_INVITATION_EXPIRED   : 이미 사용 또는 만료
- 403 CONTRACT_USER_NOT_READY       : 가입 미완료 (보호자 검증 미완료 / withdrawn 등)
- 409 CONTRACT_NOT_IN_SHARED_STATE  : 계약이 SHARED 상태 아님
  """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "당사자 연결 완료 (idempotent)",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ContractResponse::class),
                        examples = [
                            ExampleObject(
                                name = "SHARED 상태 그대로 (수신자 BUYER 연결)",
                                value = ContractExamples.SHARED_RESPONSE,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "가입 완료되지 않은 사용자",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(
                                name = "user.status != ACTIVE 또는 미성년 보호자 미검증",
                                value = ContractExamples.USER_NOT_READY,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "토큰 없음",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(
                                name = "토큰 없음",
                                value = ContractExamples.INVITATION_NOT_FOUND,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "현재 SHARED 상태 아님",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(
                                name = "RECEIVER_SIGNED / REVISION_REQUESTED / CANCELLED 등",
                                value = ContractExamples.NOT_IN_SHARED_STATE,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "410",
                description = "이미 사용 또는 만료된 토큰",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(
                                name = "만료된 토큰",
                                value = ContractExamples.INVITATION_EXPIRED,
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/invitations/{token}/accept")
    fun acceptInvitation(
        @Parameter(hidden = true) userId: Long,
        @PathVariable token: String,
    ): ContractResponse

    @Operation(
        operationId = "contractRevertToDraft",
        summary = "READY → DRAFT 되돌림",
        description = """
READY 상태의 계약을 다시 DRAFT 로 되돌립니다 (본인이 수정 재개).

조건:
- READY 상태여야 함 (DRAFT 면 409, SHARED 이상이면 409 — 공유/서명 단계 진입 후 본문 수정 차단)

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

    @Operation(
        operationId = "contractPdfDownload",
        summary = "PDF 다운로드 URL 발급",
        description = """
계약 본문 PDF 의 presigned GET URL 발급. TTL 10분.

  권한:
  - 생성자 (creator) OR 계약 당사자 (contract_parties 멤버, 수신자 accept 후) 만 접근
  - 그 외 user 는 403 NOT_ACCESSIBLE

  상태별 정책:
  - IN_PROGRESS / DRAFT : PDF 없음 → 409 (DRAFT 미리보기는 `/preview` 별도 endpoint 사용)
  - READY              : 생성자만 (수신자 아직 없음), inline 렌더링
  - SHARED ~ SIGNED    : 양측 inline 렌더링 (앱/브라우저 내 미리보기)
  - COMPLETED          : 양측 attachment 다운로드 (거래 완료 후 보존본)
  - CANCELLED          : 양측 inline 접근 가능 (audit)

  응답 사용:
  - inline : 즉시 GET → 앱 PDF 뷰어 또는 브라우저 inline 렌더링
  - attachment : 즉시 GET → 다운로드 다이얼로그
  - 다운로드 후 sha256 비교로 무결성 검증 (분쟁 시 증거 매칭)

  S3 Versioning ON 이므로 markReady 마다 새 버전. 이 endpoint 는 항상 **최신 버전** 반환.
                """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "presigned URL 발급",
                content = [
                    Content(
                        schema = Schema(implementation = ContractPdfDownloadResponse::class),
                        examples = [ExampleObject(name = "url", value = ContractExamples.PDF_DOWNLOAD_RESPONSE)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "접근 권한 없음 (creator 도 아니고 party 도 아님)",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "notAccessible", value = ContractExamples.NOT_ACCESSIBLE),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "PDF 미생성 (markReady 필요)",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "notGenerated", value = ContractExamples.PDF_NOT_GENERATED)],
                    ),
                ],
            ),
        ],
    )
    @GetMapping("/{publicCode}/pdf")
    fun pdfDownload(
        userId: Long,
        @PathVariable publicCode: String,
    ): ContractPdfDownloadResponse

    @Operation(
        operationId = "contractPreviewPdf",
        summary = "DRAFT 미리보기 PDF (markReady 전)",
        description = """
DRAFT 상태에서 markReady 전 PDF 미리보기. 같은 템플릿 / 동일 렌더링 → 최종 PDF 와 시각적으로 100% 일치.

조건:
- DRAFT 상태에서만 호출 가능. READY 이상은 `GET /pdf` 사용 (S3 영구 버전)
- markReady 와 동일 검증: title / price / conditionSummary / conditionDetails 모두 채워져 있어야 함 (누락 시 400)
- 미성년자: 보호자 동의 완료되어 있어야 함 (미완료 시 409)
- 본인 계약만 (403)

특징:
- S3 업로드 X — 매번 새로 렌더링 후 byte stream 직접 응답
- response Content-Type: application/pdf
- 브라우저/Flutter PDF viewer 에서 직접 표시 가능

활용:
- "수정하기" / "생성하기" 버튼 있는 미리보기 화면
- 사용자가 markReady 클릭 직전 마지막 검토
                """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "미리보기 PDF byte stream",
                content = [
                    Content(
                        mediaType = "application/pdf",
                        schema = Schema(type = "string", format = "binary"),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "필수 필드 누락 (markReady 조건 미충족)",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "notReady", value = ContractExamples.NOT_READY_ELIGIBLE)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "DRAFT 아님 또는 보호자 동의 미완료",
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
    @GetMapping("/{publicCode}/preview", produces = ["application/pdf"])
    fun previewPdf(
        userId: Long,
        @PathVariable publicCode: String,
    ): ResponseEntity<ByteArray>
}
