package com.trana.user

import com.trana.common.exception.ProblemDetailResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping

@Tag(name = "User", description = "사용자")
interface UserApi {
    @Operation(summary = "본인 정보 조회", description = "JWT subject(userId)로 본인 정보 반환")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [
                    Content(
                        schema = Schema(implementation = MeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "success",
                                value = """
                                      {
                                        "publicCode": "Vh7sK2x9Pq3R",
                                        "email": "user@example.com",
                                        "nickname": "홍길동",
                                        "status": "ACTIVE"
                                      }
                                  """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "토큰 없음 / 만료 / 위변조",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(
                                name = "unauthorized",
                                value = """
                                      {
                                        "type": "about:blank",
                                        "title": "Unauthorized",
                                        "status": 401,
                                        "detail": "인증이 필요합니다",
                                        "code": "AUTH_401",
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
    @GetMapping("/me")
    fun getMe(userId: Long): MeResponse

    @Operation(
        summary = "미성년자 자기보고",
        description =
            "사용자가 본인을 미성년자로 선언. 이후 보호자 KYC 흐름으로 진입. " +
                "이미 ageGroup이 설정된 사용자는 거부 (재선언 불가).",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "선언 성공 — ageGroup=MINOR로 마킹된 본인 정보 반환",
                content = [
                    Content(
                        schema = Schema(implementation = MeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "success",
                                value = """
                                  {
                                    "publicCode": "Vh7sK2x9Pq3R",
                                    "email": "minor@example.com",
                                    "nickname": "미성년자",
                                    "status": "ACTIVE",
                                    "ageGroup": "MINOR",
                                    "guardianVerifiedAt": null
                                  }
                              """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "토큰 없음 / 만료 / 위변조",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "500",
                description = "이미 ageGroup이 설정된 사용자 (재선언 거부) 등",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @PostMapping("/me/declare-minor")
    fun declareMinor(userId: Long): MeResponse
}
