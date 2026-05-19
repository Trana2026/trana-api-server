package com.trana.terms.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.terms.ConsentExamples
import com.trana.terms.dto.AgreeRequest
import com.trana.terms.dto.ConsentBatchResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import org.springframework.web.bind.annotation.RequestBody as SpringRequestBody

@Tag(name = "Consent", description = "약관 동의")
interface ConsentApi {
    @Operation(
        summary = "약관 동의",
        description = """
본인 약관 동의 (ADULT만). 미성년자 본인 동의는 거부 — 보호자가 별도 endpoint로 대리 동의.

흐름별 signupSessionId 동작:
- **성인 가입 (비인증, 첫 호출)**: 서버가 새 UUID 발급 → 응답에 포함. KYC 호출에 그대로 전달
- **성인 가입 (비인증, 추가 약관 동의)**: 기존 signupSessionId 함께 보내면 같은 세션에 누적
- **인증 사용자 (마케팅 등 추가 동의)**: JWT 인증된 userId로 즉시 매칭 → signupSessionId 무시, 응답에 null

차단:
- 미성년자 본인 동의 (ageGroup=MINOR) → 400
            """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "동의 성공",
                content = [
                    Content(
                        schema = Schema(implementation = ConsentBatchResponse::class),
                        examples = [
                            ExampleObject(
                                name = "adultSignup",
                                summary = "성인 가입 흐름 (signupSessionId 발급)",
                                value = ConsentExamples.RESPONSE_ADULT_SIGNUP,
                            ),
                            ExampleObject(
                                name = "authenticated",
                                summary = "인증 사용자 (signupSessionId=null)",
                                value = ConsentExamples.RESPONSE_AUTHENTICATED,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "미성년자 본인 동의 거부",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "minorNotAllowed", value = ConsentExamples.MINOR_NOT_ALLOWED),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "약관 ID 없음",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "termsNotFound", value = ConsentExamples.TERMS_NOT_FOUND),
                        ],
                    ),
                ],
            ),
        ],
    )
    @PostMapping
    fun agree(
        @SwaggerRequestBody(
            required = true,
            content = [
                Content(
                    schema = Schema(implementation = AgreeRequest::class),
                    examples = [
                        ExampleObject(
                            name = "adultSignup",
                            summary = "성인 가입 흐름 (signupSessionId 없이 첫 호출)",
                            value = ConsentExamples.REQUEST_ADULT_SIGNUP,
                        ),
                        ExampleObject(
                            name = "adultSignupResume",
                            summary = "성인 가입 흐름 (이미 발급된 signupSessionId 재사용)",
                            value = ConsentExamples.REQUEST_ADULT_SIGNUP_RESUME,
                        ),
                        ExampleObject(
                            name = "marketing",
                            summary = "인증 사용자 마케팅 동의",
                            value = ConsentExamples.REQUEST_MARKETING_AUTHENTICATED,
                        ),
                    ],
                ),
            ],
        )
        @SpringRequestBody
        @Valid
        request: AgreeRequest,
        @Parameter(hidden = true) userId: Long?,
        httpRequest: HttpServletRequest,
    ): ConsentBatchResponse
}
