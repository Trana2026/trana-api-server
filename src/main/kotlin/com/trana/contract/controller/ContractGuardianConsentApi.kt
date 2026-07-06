package com.trana.contract.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.contract.ContractExamples
import com.trana.contract.dto.ApproveContractGuardianConsentRequest
import com.trana.contract.dto.ContractGuardianConsentApprovedResponse
import com.trana.contract.dto.ContractGuardianConsentLinkResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@Tag(name = "Contract Guardian Consent", description = "미성년자 계약의 보호자 동의 흐름 (link 발급 + KYC 후 approve)")
interface ContractGuardianConsentApi {
    @Operation(
        operationId = "contractGuardianConsentRequest",
        summary = "보호자 동의 링크 발급 (미성년자)",
        description = """
본인 미성년 계약 (IN_PROGRESS / DRAFT + 미동의) 에 대해 보호자 동의용 토큰 발급 (항상 선택 — 미완료여도 서명 가능).

흐름:
- 응답 verifyUrl 을 카카오톡/SMS 로 보호자에게 공유
- 보호자가 trana-web 에서 토큰 URL 진입 → 약관 동의 → /guardian-consent/approve 호출
- approve 시 보호자 신원은 미성년의 가입 단계 보호자 PASS verification 자동 매핑 (매번 full PASS 안 함)

주의:
- 재발급 가능 (기존 active 토큰 강제 만료 X — TTL 자연 만료)
- 성인 호출은 400 InvalidConsentType
              """,
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "링크 발급 성공",
                content = [
                    Content(
                        schema = Schema(implementation = ContractGuardianConsentLinkResponse::class),
                        examples = [
                            ExampleObject(name = "issued", value = ContractExamples.GUARDIAN_CONSENT_LINK_CREATED),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "성인 호출 (보호자 동의 불필요)",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(
                                name = "invalidConsentType",
                                value = ContractExamples.GUARDIAN_CONSENT_INVALID_CONSENT_TYPE,
                            ),
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
                description = "DRAFT 아님 또는 이미 동의됨",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "notDraft", value = ContractExamples.NOT_DRAFT),
                            ExampleObject(name = "already", value = ContractExamples.GUARDIAN_CONSENT_ALREADY),
                        ],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/{publicCode}/guardian-consent")
    fun request(
        userId: Long,
        @PathVariable publicCode: String,
    ): ContractGuardianConsentLinkResponse

    @Operation(
        operationId = "contractReceiverGuardianConsentRequest",
        summary = "보호자 동의 링크 발급 (수신자 미성년 party)",
        description = """
미성년 receiver(party 멤버) 가 본인 계약의 보호자 동의 토큰 발급. invitation accept 후 ~ 서명 전 단계에서 호출.

흐름:
- 수신자(미성년) 가 본 endpoint 호출 → token 발급
- token URL 을 본인 보호자에게 공유
- 보호자가 web 에서 token 클릭 → 약관 동의 → `POST /v1/contracts/guardian-consent/approve` (기존 endpoint)
- approve 시 보호자 신원은 receiver(미성년) 의 가입 단계 보호자 KYC verification 자동 매핑 → `contract_parties.guardian_consent_at` + `guardian_user_id` 채움
- 이후 수신자가 서명 endpoint 호출 → backend 가 본인 party.guardianConsentAt != null 검증

권한:
- 본인이 contract_parties 멤버 (creator 본인 호출 시 403 → 기존 `/guardian-consent` endpoint 사용)
- 본인이 미성년 (성인 party 호출 시 400 InvalidConsentType)
- party.guardianConsentAt 이미 채워져 있으면 409 GuardianConsentAlready
                """,
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "링크 발급 성공",
                content = [
                    Content(
                        schema = Schema(implementation = ContractGuardianConsentLinkResponse::class),
                        examples = [
                            ExampleObject(
                                name = "issued",
                                value = ContractExamples.GUARDIAN_CONSENT_LINK_CREATED,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "성인 party 가 호출 (보호자 동의 불요)",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(
                                name = "invalidConsentType",
                                value = ContractExamples.GUARDIAN_CONSENT_INVALID_CONSENT_TYPE,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "party 멤버 아님 또는 creator 본인 호출",
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
                description = "이미 동의됨",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "already", value = ContractExamples.GUARDIAN_CONSENT_ALREADY),
                        ],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/{publicCode}/receiver-guardian-consent")
    fun requestReceiverConsent(
        @Parameter(hidden = true) userId: Long,
        @PathVariable publicCode: String,
    ): ContractGuardianConsentLinkResponse

    @Operation(
        operationId = "contractGuardianConsentApprove",
        summary = "보호자 동의 확정 (public — web 단순 동의)",
        description = """
보호자가 web 에서 token URL 클릭 + 약관 동의 클릭 시 호출. JWT 미요구 (보호자는 user 아님).

흐름:
- 토큰 검증 (active + purpose=CONTRACT_CONSENT)
- 미성년 user 의 가입 단계 보호자 (identity_verifications.purpose=GUARDIAN + SUCCESS) ID 자동 매핑
- contract.markGuardianConsented(guardianId) + link.markUsed()
- 응답으로 publicCode + 동의 시각만 반환 (최소 정보)

주의:
- 보호자 신원 확인은 가입 단계 보호자 KYC 1회로 끝 — 계약마다 또 eKYC 안 함 (2026-06-01 정책 변경)
- 가입 단계 보호자 KYC 미완료 미성년 시 409 GuardianConsentRequired
- 토큰 1회용 — approve 후 markUsed → 재호출 시 410
                """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "동의 처리 완료",
                content = [
                    Content(
                        schema = Schema(implementation = ContractGuardianConsentApprovedResponse::class),
                        examples = [
                            ExampleObject(
                                name = "approved",
                                value = ContractExamples.GUARDIAN_CONSENT_APPROVE_RESPONSE,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "토큰 purpose 잘못됨 (CONTRACT_CONSENT 이외)",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(
                                name = "invalidConsentType",
                                value = ContractExamples.GUARDIAN_CONSENT_INVALID_CONSENT_TYPE,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "이미 동의됨",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "already", value = ContractExamples.GUARDIAN_CONSENT_ALREADY),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "410",
                description = "토큰 사용/만료",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "linkInvalid", value = ContractExamples.GUARDIAN_CONSENT_LINK_INVALID),
                        ],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/guardian-consent/approve")
    fun approve(
        @RequestBody @Valid request: ApproveContractGuardianConsentRequest,
    ): ContractGuardianConsentApprovedResponse
}
