package com.trana.terms.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(description = "단일 약관 동의 기록")
data class ConsentResponse(
    @Schema(description = "동의 기록 ID", example = "1")
    val id: Long,
    @Schema(description = "동의한 약관 버전 ID", example = "1")
    val termsVersionId: Long,
    @Schema(description = "동의 시각 (UTC)")
    val agreedAt: Instant,
)

@Schema(description = "약관 동의 batch 응답")
data class ConsentBatchResponse(
    @Schema(
        description = "성인 가입 흐름 발급 세션 ID. KYC 호출에 전달. 인증 사용자(미성년자 등)는 null",
        nullable = true,
    )
    val signupSessionId: UUID?,
    @Schema(description = "동의 기록 목록")
    val consents: List<ConsentResponse>,
)
