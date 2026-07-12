package com.trana.contract.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import java.time.Instant

@Schema(description = "미성년자 계약 상대방(성인) 위험 고지 확인 요청 본문")
data class ConfirmMinorDisclosureRequest(
    @field:Schema(
        description = "프론트가 위험 고지 화면을 표시한 시각 (UTC). 서버 확인 클릭 시각과 함께 audit 저장",
        example = "2026-07-10T12:34:00Z",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val disclosedAt: Instant,
    @field:Size(max = 20)
    @field:Schema(
        description =
            "프론트가 표시한 문구 버전. 서버 LATEST_VERSION 과 다르면 재고지 유도. " +
                "미전송 시 서버 최신 버전으로 저장",
        example = "v1",
        nullable = true,
    )
    val templateVersion: String? = null,
)

@Schema(description = "미성년자 계약 상대방 위험 고지 확인 응답")
data class MinorDisclosureConfirmationResponse(
    @Schema(description = "확인 처리 완료 서버 시각", example = "2026-07-10T12:34:56Z")
    val confirmedAt: Instant,
    @Schema(description = "저장된 문구 버전", example = "v1")
    val templateVersion: String,
)
