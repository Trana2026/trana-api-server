package com.trana.terms

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping

@Tag(name = "Terms", description = "약관 조회")
interface TermsApi {
    @Operation(
        summary = "활성 약관 목록 조회",
        description = "현재 시행 중인 약관 (type별 최신 버전 1개씩). 공개 endpoint.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "활성 약관 목록",
                content = [
                    Content(
                        array = ArraySchema(schema = Schema(implementation = TermsResponse::class)),
                        examples = [
                            ExampleObject(
                                name = "success",
                                value = """
                                      [
                                        {
                                          "id": 1,
                                          "type": "SERVICE",
                                          "version": "1.0",
                                          "title": "TRANA 서비스 이용약관",
                                          "contentUrl": "https://trana.com/terms/service/1.0",
                                          "effectiveAt": "2026-05-01T00:00:00Z"
                                        },
                                        {
                                          "id": 2,
                                          "type": "PRIVACY",
                                          "version": "1.0",
                                          "title": "개인정보 처리방침",
                                          "contentUrl": "https://trana.com/terms/privacy/1.0",
                                          "effectiveAt": "2026-05-01T00:00:00Z"
                                        }
                                      ]
                                  """,
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @GetMapping
    fun getActiveTerms(): List<TermsResponse>
}
