package com.trana.guardian

import com.trana.common.exception.ProblemDetailResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping

@Tag(name = "Guardian", description = "보호자 매칭 (미성년자 가입 보호자 인증)")
interface GuardianApi {
    @Operation(
        summary = "보호자 매칭 링크 발급",
        description =
            "미성년자가 본인 인증을 위해 보호자에게 전달할 토큰 발급. " +
                "기존 활성 토큰은 자동 만료(revoke). TTL 3일.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "발급 성공"),
            ApiResponse(
                responseCode = "401",
                description = "토큰 없음 / 만료 / 위변조",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "미성년자가 아닌 사용자 (GUARDIAN_403_NOT_MINOR)",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "이미 보호자 인증 완료된 사용자 (GUARDIAN_409_VERIFIED)",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/links")
    fun createLink(
        @Parameter(hidden = true) userId: Long,
    ): GuardianLinkCreateResponse

    @Operation(
        summary = "보호자 매칭 링크 조회",
        description =
            "보호자가 링크 클릭 시 토큰 유효성 검증. 인증 불필요 (토큰 자체가 비밀). " +
                "유효 → 200, 없음 → 404, 만료/사용/취소 → 410",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "토큰 유효"),
            ApiResponse(
                responseCode = "404",
                description = "토큰 없음 (GUARDIAN_404_LINK)",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "410",
                description = "토큰 만료/사용/취소 (GUARDIAN_410_LINK)",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @GetMapping("/links/{token}")
    fun getLink(
        @PathVariable("token") token: String,
    ): GuardianLinkInfoResponse
}
