package com.trana.guardian

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "보호자 신분증 진위확인 요청")
data class GuardianIdCardVerifyRequest(
    @Schema(
        description = "OCR 단계에서 받은 requestId (10분 유효)",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    @NotBlank(message = "requestId는 필수입니다")
    val requestId: String,
    @Schema(
        description = "보호자 매칭 토큰 (21자)",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    @NotBlank(message = "token은 필수입니다")
    val token: String,
)
