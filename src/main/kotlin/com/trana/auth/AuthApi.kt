package com.trana.auth

import com.trana.common.exception.ProblemDetailResponse
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

@Tag(name = "Auth", description = "인증 (소셜 로그인 / 토큰)")
interface AuthApi {
    @Operation(
        summary = "소셜 로그인 (가입 + 로그인 통합)",
        description = """
  클라이언트(Flutter)가 받아온 공급자 id_token (OIDC JWT)으로 우리 서버 인증.
  - 신규 사용자: 자동 가입 + JWT 발급
  - 기존 사용자: JWT 재발급
  - 지원 공급자: KAKAO, GOOGLE (APPLE 추후)
  - 사전 조건: 공급자 OIDC 활성화 + Flutter SDK가 openid scope 요청
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
}
