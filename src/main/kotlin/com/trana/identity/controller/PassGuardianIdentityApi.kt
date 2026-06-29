package com.trana.identity.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.identity.PassExamples
import com.trana.identity.dto.MOKReqClientInfoResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

@Tag(name = "Guardian Identity PASS", description = "보호자 PASS (mobileOK V3 표준창) 본인확인 — 미성년 가입 완성")
interface PassGuardianIdentityApi {
    @Operation(
        operationId = "passGuardianReqClientInfo",
        summary = "보호자 PASS 표준창 요청 토큰 발급 (Step 1)",
        description = """
trana-web-guardian 에서 호출. 자녀가 발급한 GuardianLink 토큰 (jnanoid 21자) 필수.

V3 표준창 SDK 호출 방식 — `MOBILEOK.process(URL, browserDevice, callback)` 의 첫 인자가 이 endpoint URL.
SDK 가 해당 URL 에 POST 보내서 응답 body 의 MOKReqClientInfo JSON 을 받아 표준창에 전달.
따라서 token 은 query param (`?token=...`).

흐름:
- token 유효성 (TTL 3일, 미사용) + 자녀 user MINOR + guardian_verified_at=null 검증
- clientTxId 발급 + PENDING IdentityVerification (purpose=GUARDIAN, subjectUserId, guardianLinkToken) INSERT
- mok_keyInfo.dat 의 ServerPublicKey 로 토큰 RSA-OAEP 암호화
- MOKReqClientInfo 응답 — returnUrl 은 본인 흐름과 통합 endpoint (/v1/identity/pass/return)
→ 그 endpoint 가 verification.purpose 분기 → GUARDIAN path 처리 (보호자 매핑 + 자녀 markGuardianVerified + 푸시)

이후 흐름:
- trana-web-guardian MOBILEOK.process("https://.../v1/identity/guardian/pass/req-client-info?token=...", "WB", callback) 호출 → 표준창 팝업
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
        @Parameter(
            name = "token",
            `in` = ParameterIn.QUERY,
            required = true,
            description = "GuardianLink token (jnanoid 21자)",
            example = "V1StGXR8_Z5jdHi6B-myT",
        )
        @RequestParam("token")
        @NotBlank token: String,
    ): MOKReqClientInfoResponse
}
