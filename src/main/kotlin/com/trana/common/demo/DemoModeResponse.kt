package com.trana.common.demo

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "데모 모드 조회 응답")
data class DemoModeResponse(
    @Schema(description = "데모 모드 활성 여부", example = "true")
    val enabled: Boolean,
)
