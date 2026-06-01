package com.trana.contract.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.time.Instant

@Schema(description = "보호자 동의 링크 발급 응답 (미성년자 → 보호자 공유용)")
data class ContractGuardianConsentLinkResponse(
    @field:Schema(description = "보호자 동의 토큰 (jnanoid 21자, TTL 3일)", example = "V1StGXR8_Z5jdHi6B-myT")
    val token: String,
    @field:Schema(description = "토큰 만료 시각")
    val expiresAt: Instant,
    @field:Schema(
        description = "보호자가 접근할 web-guardian URL (카카오톡/SMS 공유용)",
        example = "https://guardian.trana.kr/contract?token=V1StGXR8_Z5jdHi6B-myT",
    )
    val verifyUrl: String,
)

@Schema(description = "보호자 동의 확정 요청 — 보호자 web 단순 동의 (token URL 클릭 + 약관 동의)")
data class ApproveContractGuardianConsentRequest(
    @field:NotBlank
    @field:Schema(description = "발급받은 보호자 동의 토큰", example = "V1StGXR8_Z5jdHi6B-myT")
    val token: String,
)

@Schema(description = "보호자 동의 확정 응답 — 최소한의 confirmation")
data class ContractGuardianConsentApprovedResponse(
    @field:Schema(description = "동의 처리된 계약 publicCode", example = "Vh7sK2x9Pq3R")
    val publicCode: String,
    @field:Schema(description = "동의 처리 시각 (UTC)")
    val guardianConsentAt: Instant,
)
