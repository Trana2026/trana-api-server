package com.trana.guardian.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.guardian.GuardianExamples
import com.trana.guardian.dto.GuardianLinkResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping

@Tag(name = "Guardian", description = "보호자 매칭")
interface GuardianApi {
    @Operation(
        operationId = "guardianCreateLink",
        summary = "보호자 링크 발급 (미성년자 → 보호자)",
        description = """
미성년자가 자신의 보호자에게 공유할 일회용 토큰 발급.

흐름:
- JWT 인증 필요 (미성년자 본인)
- 발급된 토큰을 verifyUrl 형태로 받음 → 카카오톡/SMS 등으로 보호자에게 전달
- 보호자가 URL 접근 → trana-web-guardian이 Phase 6 보호자 KYC endpoint 호출
- 토큰은 3일 TTL, 1회 사용 (Compare SUCCESS 시 markUsed)

차단:
- 성인 사용자(ageGroup=ADULT) → 403 NOT_MINOR
- 이미 보호자 인증 완료 → 409 ALREADY_VERIFIED
            """,
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "발급 성공",
                content = [
                    Content(
                        schema = Schema(implementation = GuardianLinkResponse::class),
                        examples = [ExampleObject(name = "success", value = GuardianExamples.LINK_SUCCESS)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "JWT 인증 필요",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "unauthorized", value = GuardianExamples.UNAUTHORIZED)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "성인은 발급 불가",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "notMinor", value = GuardianExamples.NOT_MINOR)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "이미 보호자 인증 완료",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "alreadyVerified", value = GuardianExamples.ALREADY_VERIFIED),
                        ],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/links")
    fun createLink(userId: Long): GuardianLinkResponse
}
