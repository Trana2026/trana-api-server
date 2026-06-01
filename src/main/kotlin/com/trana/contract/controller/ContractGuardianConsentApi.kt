package com.trana.contract.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.contract.ContractExamples
import com.trana.contract.dto.ApproveContractGuardianConsentRequest
import com.trana.contract.dto.ContractGuardianConsentApprovedResponse
import com.trana.contract.dto.ContractGuardianConsentLinkResponse
import io.swagger.v3.oas.annotations.Operation
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
본인 계약(GUARDIAN_REQUIRED + DRAFT + 미동의)에 대해 보호자 동의용 토큰 발급.

흐름:
- 응답 verifyUrl 을 카카오톡/SMS 로 보호자에게 공유
- 보호자가 trana-web-guardian 에서 토큰으로 KYC 진행
- Compare SUCCESS 후 보호자가 /guardian-consent/approve 호출

주의:
- 재발급 가능 (기존 active 토큰 강제 만료 X — TTL 자연 만료)
- 성인 계약(consentType=NOT_APPLICABLE) 호출은 400
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
                description = "보호자 동의가 불필요한 계약",
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
                description = "토큰 purpose 잘못됨 또는 계약이 NOT_APPLICABLE",
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
