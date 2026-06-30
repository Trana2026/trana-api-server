package com.trana.guardian.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "보호자 링크 발급 응답")
data class GuardianLinkResponse(
    @Schema(description = "일회용 토큰 (jnanoid 21자)", example = "V1StGXR8_Z5jdHi6B-myT")
    val token: String,
    @Schema(description = "TTL 3일 후 만료 시각")
    val expiresAt: Instant,
    @Schema(
        description = "보호자가 접근할 URL — 미성년자가 보호자에게 공유 (KakaoTalk/SMS 등)",
        example = "https://dev-kyc.trana.kr/verify/V1StGXR8_Z5jdHi6B-myT?openExternalBrowser=1",
    )
    val verifyUrl: String,
)
