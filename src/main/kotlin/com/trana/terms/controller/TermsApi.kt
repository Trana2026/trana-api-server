package com.trana.terms.controller

import com.trana.terms.dto.TermsResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping

@Tag(name = "Terms", description = "약관")
interface TermsApi {
    @Operation(
        summary = "활성 약관 조회",
        description = "현재 시행 중인 약관 목록. type별 최신 1개. 비인증.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공"),
        ],
    )
    @GetMapping
    fun getActiveTerms(): List<TermsResponse>
}
