package com.trana.terms

import com.trana.common.exception.ProblemDetailResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@Tag(name = "Consent", description = "약관 동의")
interface ConsentApi {
    @Operation(
        summary = "약관 동의",
        description = "여러 약관에 한 번에 동의. IP / User-Agent는 서버가 HTTP 요청에서 자동 추출 (법적 증거).",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "동의 성공",
                content = [
                    Content(
                        array = ArraySchema(schema = Schema(implementation = ConsentResponse::class)),
                        examples = [
                            ExampleObject(
                                name = "success",
                                value = """
                                      [
                                        {"id": 1, "termsVersionId": 1, "agreedAt": "2026-05-07T14:30:00Z"},
                                        {"id": 2, "termsVersionId": 2, "agreedAt": "2026-05-07T14:30:00Z"}
                                      ]
                                  """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "약관 ID가 존재하지 않음",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(
                                name = "termsNotFound",
                                value = """
                                      {
                                        "type": "about:blank",
                                        "title": "TERMS_404",
                                        "status": 404,
                                        "detail": "약관을 찾을 수 없습니다 (id=999)",
                                        "code": "TERMS_404",
                                        "timestamp": "2026-05-07T14:30:00Z"
                                      }
                                  """,
                            ),
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
                            name = "signupAdult",
                            summary = "가입 시 성인 동의",
                            value = """
                                  {
                                    "termsVersionIds": [1, 2],
                                    "contextType": "SIGNUP",
                                    "ageGroup": "ADULT"
                                  }
                              """,
                        ),
                    ],
                ),
            ],
        )
        @RequestBody
        request: AgreeRequest,
        userId: Long,
        httpRequest: HttpServletRequest,
    ): List<ConsentResponse>
}
