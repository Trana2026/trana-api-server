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
        summary = "소셜 로그인 (미성년자 가입 + 로그인)",
        description = """
미성년자 가입 전용 흐름. 성인은 본인 KYC 흐름으로 가입 (이 endpoint 호출 X).
  - 신규 미성년자: 자동 가입 (age_group=MINOR) + JWT 발급
  - 기존 사용자: JWT 재발급
  - 지원 공급자: KAKAO, GOOGLE, APPLE
  - 사전 조건: 공급자 OIDC 활성화 + Flutter SDK가 openid scope 요청
  - APPLE 의 경우: nonce 필수 (raw nonce 전송, 백엔드가 SHA256 후 id_token claim 과 비교)
  - APPLE Android: /v1/auth/apple/callback 거쳐 Flutter deeplink → 본 endpoint 호출 (iOS native 는 직접 호출)
  - body의 ageGroup은 현재 MINOR만 허용 (성인 호출 시 거부)
      """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "로그인 성공",
                content = [
                    Content(
                        schema = Schema(implementation = SignInResponse::class),
                        examples = [ExampleObject(name = "success", value = AuthExamples.SIGN_IN_SUCCESS)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "invalidBody", value = AuthExamples.INVALID_BODY)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "공급자 토큰 검증 실패",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(
                                name = "invalidSocialToken",
                                value = AuthExamples.INVALID_SOCIAL_TOKEN,
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/social/sign-in")
    fun socialSignIn(
        @SwaggerRequestBody(
            required = true,
            content = [
                Content(
                    schema = Schema(implementation = SocialSignInRequest::class),
                    examples = [
                        ExampleObject(name = "kakao", value = AuthExamples.REQUEST_KAKAO),
                        ExampleObject(name = "google", value = AuthExamples.REQUEST_GOOGLE),
                        ExampleObject(name = "apple", value = AuthExamples.REQUEST_APPLE),
                    ],
                ),
            ],
        )
        @org.springframework.web.bind.annotation.RequestBody
        @Valid
        request: SocialSignInRequest,
    ): SignInResponse

    @Operation(
        summary = "토큰 재발급",
        description = """
Refresh token으로 새 access / refresh token 발급.
- access token 만료 (15분) 후 사용
- refresh token 만료 (30일) 시엔 다시 sign-in 필요
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
