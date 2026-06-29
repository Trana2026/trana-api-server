package com.trana.identity.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.identity.PassExamples
import com.trana.identity.dto.MOKReqClientInfoResponse
import com.trana.identity.dto.PassGuardianReqClientInfoRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import org.springframework.web.bind.annotation.RequestBody as SpringRequestBody

@Tag(name = "Guardian Identity PASS", description = "보호자 PASS (mobileOK V3 표준창) 본인확인 — 미성년 가입 완성")
interface PassGuardianIdentityApi {
    @Operation(
        operationId = "passGuardianReqClientInfo",
        summary = "보호자 PASS 표준창 요청 토큰 발급 (Step 1)",
        description = """
trana-web-guardian 에서 호출. 자녀가 발급한 GuardianLink 토큰 (jnanoid 21자) 필수.

흐름:
- token 유효성 (TTL 3일, 미사용) + 자녀 user MINOR + guardian_verified_at=null 검증
- clientTxId 발급 + PENDING IdentityVerification (purpose=GUARDIAN, subjectUserId, guardianLinkToken) INSERT
- mok_keyInfo.dat 의 ServerPublicKey 로 토큰 RSA-OAEP 암호화
- MOKReqClientInfo 응답 — returnUrl 은 본인 흐름과 통합 endpoint (/v1/identity/pass/return)
→ 그 endpoint 가 verification.purpose 분기 → GUARDIAN path 처리 (보호자 매핑 + 자녀 markGuardianVerified + 푸시)

이후 흐름:
- trana-web-guardian MOBILEOK.process() 호출 → 표준창 팝업
- 보호자 본인 통신사 인증 → 표준창이 returnUrl POST
- 백엔드 처리 후 trana-web-guardian /pass/result#status=success&minorPublicCode=... 로 redirect
            """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "MOKReqClientInfo 발급 성공",
                content = [
                    Content(
                        schema = Schema(implementation = MOKReqClientInfoResponse::class),
                        examples = [ExampleObject(name = "success", value = PassExamples.REQ_CLIENT_INFO_SUCCESS)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "GuardianLink token 없음",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "linkNotFound", value = PassExamples.GUARDIAN_LINK_NOT_FOUND),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "410",
                description = "GuardianLink 만료/사용됨",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "linkInvalid", value = PassExamples.GUARDIAN_LINK_INVALID),
                        ],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/req-client-info")
    fun requestGuardianClientInfo(
        @SwaggerRequestBody(
            required = true,
            content = [
                Content(
                    schema = Schema(implementation = PassGuardianReqClientInfoRequest::class),
                    examples = [
                        ExampleObject(name = "default", value = PassExamples.GUARDIAN_REQ_CLIENT_INFO_REQUEST),
                    ],
                ),
            ],
        )
        @SpringRequestBody
        @Valid
        request: PassGuardianReqClientInfoRequest,
    ): MOKReqClientInfoResponse
}
