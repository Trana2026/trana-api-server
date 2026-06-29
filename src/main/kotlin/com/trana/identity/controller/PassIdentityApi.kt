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
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import java.util.UUID

@Tag(name = "Identity PASS", description = "PASS (mobileOK V3 표준창) 본인확인 — 성인/미성년 통합")
interface PassIdentityApi {
    @Operation(
        operationId = "passReqClientInfo",
        summary = "PASS 표준창 요청 토큰 발급 (Step 1)",
        description = """
약관 동의 (signupSessionId) 보유 사용자가 PASS 표준창 호출 직전에 요청.

V3 표준창 SDK 호출 방식 — `MOBILEOK.process(URL, browserDevice, callback)` 의 첫 인자가 이 endpoint URL.
SDK 가 해당 URL 에 POST 보내서 응답 body 의 MOKReqClientInfo JSON 을 받아 표준창에 전달.
따라서 signupSessionId 는 query param (`?signupSessionId=...`).

흐름 (Service):
- signupSessionId TTL (30분) 검증
- clientTxId 발급 ("TRANA-" + UUID hex 32자 = 38자)
- PENDING IdentityVerification row INSERT (NCP 필드 모두 NULL, PASS factory)
- mok_keyInfo.dat 의 ServerPublicKey 로 { version: V2, clientTxId, requestTime(KST) } RSA-OAEP 암호화
- MOKReqClientInfo 응답 조립 (serviceId + encryptReqClientInfo + serviceType + usageCode + ...)

이후 흐름:
- trana-web MOBILEOK.process("https://.../v1/identity/pass/req-client-info?signupSessionId=...", "WB", callback) 호출
- 사용자 통신사 본인확인 진행 → 표준창이 returnUrl 로 결과 토큰 POST
- 백엔드 /v1/identity/pass/return 가 5초 TTL 안에 검증 + verification SUCCESS 전이 + JWT 발급 (PASS-4)
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
                responseCode = "410",
                description = "가입 세션 만료 (signupSessionId TTL 30분 초과)",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "signupExpired", value = PassExamples.SIGNUP_EXPIRED)],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/req-client-info")
    fun reqClientInfo(
        @Parameter(
            name = "signupSessionId",
            `in` = ParameterIn.QUERY,
            required = true,
            description = "가입 세션 ID (UUID, TTL 30분, 약관 동의 시 발급)",
            example = "550e8400-e29b-41d4-a716-446655440000",
        )
        @RequestParam("signupSessionId") signupSessionId: UUID,
    ): MOKReqClientInfoResponse

    @Operation(
        operationId = "passReturn",
        summary = "PASS 표준창 결과 콜백 (Step 2 — 표준창이 직접 호출, 클라이언트가 호출 X)",
        description = """
mobileOK V3 표준창이 본인확인 완료 후 POST 로 호출. 클라이언트 (Flutter/trana-web) 가 직접 호출하지 않음.

흐름:
- Content-Type: application/x-www-form-urlencoded
- Body: `data=<url-encoded JSON>` (JSON 안에 encryptMOKKeyToken)
- 백엔드: 5초 TTL 안에 mobileOK 검증 호출 → encryptMOKResult 복호화 → 무결성 검증 → user 매칭/생성 → JWT 발급 → 302 redirect

응답: 302 redirect — Location: {trana.identity.pass.result-redirect-url}#accessToken=...&refreshToken=...&publicCode=...&requiresGuardian=...

URL fragment (#) 사용 이유: 서버 로그 미기록 + 브라우저 referer 미전송 + JS 만 접근 가능.

trana-web 결과 페이지 책임:
- fragment 파싱 → localStorage 저장
- requiresGuardian=true 면 보호자 흐름 진입, false 면 home 으로 이동
          """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "302",
                description = "trana-web 결과 페이지로 redirect (성공/실패 모두 fragment 으로 전달)",
            ),
        ],
    )
    @PostMapping("/return", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun receiveReturn(
        @RequestParam("data") data: String,
    ): ResponseEntity<Void>
}
