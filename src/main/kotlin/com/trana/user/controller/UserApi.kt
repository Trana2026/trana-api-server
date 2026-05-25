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
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseStatus

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

    @Operation(
        summary = "회원 탈퇴",
        description = """
JWT subject(userId) 기준으로 회원 탈퇴 처리.

동작:
- status=WITHDRAWN + withdrawnAt 설정 (soft delete)
- 연관 데이터 (identity_verifications, user_consents, guardian_links)는 보존 (audit + 법적)
- access 토큰은 자연 만료 (15분) — 클라이언트가 로컬 토큰 폐기 필요

재가입:
- 성인: 같은 신분증으로 KYC 재진입 가능 (이전 SUCCESS verification은 ACTIVE 아닌 user 소유라 차단 안 됨)
- 미성년자: 같은 소셜 계정으로 재로그인 시 신규 user 발급 (이전 social 매핑 삭제 후 신규 생성)
          """,
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "탈퇴 성공"),
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
            ApiResponse(
                responseCode = "409",
                description = "이미 탈퇴됨 (USER_409_ALREADY_WITHDRAWN)",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/me")
    fun withdraw(
        @Parameter(hidden = true) userId: Long,
    )
}
