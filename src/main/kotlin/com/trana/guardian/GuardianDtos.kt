package com.trana.guardian

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "보호자 매칭 링크 발급 응답")
data class GuardianLinkCreateResponse(
    @Schema(
        description = "매칭 토큰 (21자 base62). 프론트가 verify/{token} URL 조립용",
        example = "V1StGXR8_Z5jdHi6BmyT9",
    )
    val token: String,
    @Schema(description = "토큰 만료 시각 (UTC, 발급 후 3일)")
    val expiresAt: Instant,
)

@Schema(description = "보호자 매칭 링크 조회 응답")
data class GuardianLinkInfoResponse(
    @Schema(description = "토큰 만료 시각 (UTC)")
    val expiresAt: Instant,
)
