package com.trana.terms.controller

import com.trana.terms.dto.TermsContentResponse
import com.trana.terms.dto.TermsResponse
import com.trana.terms.entity.TermsContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

@Tag(name = "Terms", description = "약관")
interface TermsApi {
    @Operation(
        summary = "활성 약관 조회",
        description =
            "현재 시행 중인 약관 목록. type별 최신 1개. 비인증.\n" +
                "- context 미지정: 전체 활성 약관\n" +
                "- context=CONTRACT: 계약 서명 필수 약관 (ELECTRONIC_SIGNATURE)",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공"),
        ],
    )
    @GetMapping
    fun getActiveTerms(
        @Parameter(description = "조회 컨텍스트 필터 (예: CONTRACT). 미지정 시 전체")
        @RequestParam(required = false) context: TermsContext?,
    ): List<TermsResponse>

    @Operation(
        summary = "약관 단건 + 전문 조회",
        description = "약관 ID 로 단건 조회 + 마크다운 전문 반환. 웹 약관 상세 페이지/앱이 렌더. 비인증.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공"),
            ApiResponse(responseCode = "404", description = "약관 또는 전문 없음"),
        ],
    )
    @GetMapping("/{id}")
    fun getTermContent(
        @Parameter(description = "약관 ID") @PathVariable id: Long,
    ): TermsContentResponse
}
