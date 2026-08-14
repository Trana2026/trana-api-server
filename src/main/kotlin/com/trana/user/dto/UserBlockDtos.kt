package com.trana.user.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "차단 결과 응답")
data class BlockUserResponse(
    @Schema(description = "차단당한 사용자 고유코드(shareCode)", example = "AB3K9")
    val blockedShareCode: String,
    @Schema(description = "차단 시각 (UTC)")
    val blockedAt: Instant,
)

@Schema(description = "차단한 사용자 목록 항목")
data class BlockedUserView(
    @Schema(description = "차단당한 사용자 고유코드(shareCode)", example = "AB3K9")
    val shareCode: String,
    @Schema(description = "차단 시각 (UTC)")
    val blockedAt: Instant,
)
