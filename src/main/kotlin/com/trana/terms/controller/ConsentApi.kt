package com.trana.terms.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.terms.dto.AgreeRequest
import com.trana.terms.dto.ConsentBatchResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@Tag(name = "Consent")
interface ConsentApi {
    @Operation(
        summary = "약관 동의",
        description = """
여러 약관에 한 번에 동의. IP/User-Agent는 서버가 HTTP 요청에서 자동 추출 (법적 증거).

- 비인증 호출 (성인 가입): signupSessionId 발급 → 응답에 포함
- JWT 인증 호출 (미성년자 로그인 후 동의 등): userId 자동 매칭 → signupSessionId=null
          """,
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "동의 성공"),
            ApiResponse(
                responseCode = "404",
                description = "약관 ID 없음 (TERMS_404)",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @PostMapping
    fun agree(
        @RequestBody @Valid request: AgreeRequest,
        @Parameter(hidden = true) userId: Long?,
        httpRequest: HttpServletRequest,
    ): ConsentBatchResponse
}
