package com.trana.auth

import com.trana.common.exception.ProblemDetailResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@Tag(name = "Auth", description = "인증 (소셜 로그인 / 토큰)")
interface AuthApi {
    @Operation(
        summary = "소셜 로그인 (가입 + 로그인 통합)",
        description = """
클라이언트(Flutter)가 받아온 공급자 access_token으로 우리 서버 인증.

- 신규 사용자: 자동 가입 + JWT 발급
- 기존 사용자: JWT 재발급

지원 공급자: KAKAO (W2), GOOGLE/APPLE (W2 후반)
          """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "로그인 성공 — access/refresh JWT 발급",
                content = [
                    Content(
                        schema = Schema(implementation = SignInResponse::class),
                        examples = [
                            ExampleObject(
                                name = "success",
                                summary = "로그인 성공",
                                value = """
                                      {
                                        "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJ0cmFuYSI...",
                                        "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJ0cmFuYSI...",
                                        "publicCode": "Vh7sK2x9Pq3R"
                                      }
                                  """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (지원하지 않는 공급자 등)",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(
                                name = "unsupportedProvider",
                                summary = "지원하지 않는 공급자",
                                value = """
                                      {
                                        "type": "about:blank",
                                        "title": "COMMON_400",
                                        "status": 400,
                                        "detail": "요청 본문을 파싱할 수 없습니다",
                                        "instance": "/api/v1/auth/social/sign-in",
                                        "code": "COMMON_400",
                                        "timestamp": "2026-05-06T12:34:56Z"
                                      }
                                  """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "500",
                description = "공급자 API 호출 실패 또는 서버 오류",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(
                                name = "kakaoApiFailed",
                                summary = "Kakao API 응답 오류",
                                value = """
                                      {
                                        "type": "about:blank",
                                        "title": "COMMON_500",
                                        "status": 500,
                                        "detail": "서버 오류가 발생했습니다",
                                        "code": "COMMON_500",
                                        "timestamp": "2026-05-06T12:34:56Z"
                                      }
                                  """,
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/social/sign-in")
    fun socialSignIn(@RequestBody request: SocialSignInRequest): SignInResponse
}
