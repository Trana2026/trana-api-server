package com.trana.auth

import com.trana.common.exception.ProblemDetailResponse
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
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@Tag(name = "Auth", description = "인증")
interface AuthApi {
    @Operation(
        summary = "토큰 재발급",
        description = """
  Refresh token으로 새 access / refresh token 발급.
  - access token 만료 (15분) 후 사용
  - refresh token 만료 (30일) 시엔 다시 로그인 (PASS 흐름) 필요
  - 매 호출 시 access + refresh 모두 새로 발급
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "재발급 성공",
                content = [
                    Content(
                        schema = Schema(implementation = SignInResponse::class),
                        examples = [ExampleObject(name = "success", value = AuthExamples.SIGN_IN_SUCCESS)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Refresh token 위변조 / 만료",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "invalidToken", value = AuthExamples.INVALID_TOKEN)],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/refresh")
    fun refresh(
        @SwaggerRequestBody(
            required = true,
            content = [
                Content(
                    schema = Schema(implementation = RefreshRequest::class),
                    examples = [ExampleObject(name = "default", value = AuthExamples.REQUEST_REFRESH)],
                ),
            ],
        )
        @org.springframework.web.bind.annotation.RequestBody
        @Valid
        request: RefreshRequest,
    ): SignInResponse

    @Operation(
        summary = "로그아웃",
        description = """
    JWT subject(userId) 기준 로그아웃. audit 기록 + (선택적) device token 정리.

    동작:
    - AuditEvent.USER_SIGNED_OUT 기록 (IP/UA 자동 — RequestMdcFilter)
    - deviceToken 제공 시 본인 token 매칭 row 삭제 (멱등)
    - JWT 무효화 X (stateless 정책, access 15분 자연 만료) — 클라이언트가 로컬 토큰 폐기 필요

    운영 보류 (W9+):
    - refresh token blacklist (현재 stateless 라 강제 만료 불가)
            """,
        requestBody =
            SwaggerRequestBody(
                required = true,
                content = [
                    Content(
                        schema = Schema(implementation = LogoutRequest::class),
                        examples = [
                            ExampleObject(name = "withDeviceToken", value = AuthExamples.LOGOUT_WITH_DEVICE_TOKEN),
                            ExampleObject(name = "auditOnly", value = AuthExamples.LOGOUT_AUDIT_ONLY),
                        ],
                    ),
                ],
            ),
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "로그아웃 성공"),
            ApiResponse(
                responseCode = "401",
                description = "토큰 없음 / 만료 / 위변조",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @SecurityRequirement(name = "bearerAuth")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/logout")
    fun logout(
        @Parameter(hidden = true) userId: Long,
        @org.springframework.web.bind.annotation.RequestBody
        @jakarta.validation.Valid
        request: LogoutRequest,
    )
}
