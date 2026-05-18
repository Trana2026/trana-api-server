package com.trana.user.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.user.dto.MeResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping

@Tag(name = "User", description = "사용자")
interface UserApi {
    @Operation(
        summary = "본인 정보 조회",
        description = "JWT subject(userId) 기준으로 본인 정보 반환. 미성년자 가입 완료 폴링용 guardianVerifiedAt 포함.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공"),
            ApiResponse(
                responseCode = "401",
                description = "토큰 없음 / 만료 / 위변조",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "사용자 없음 (USER_404)",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @GetMapping("/me")
    fun getMe(
        @Parameter(hidden = true) userId: Long,
    ): MeResponse
}
