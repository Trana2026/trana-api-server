package com.trana.terms.dto

import com.trana.terms.entity.ConsentContextType
import com.trana.user.entity.AgeGroup
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import java.util.UUID

@Schema(description = "약관 동의 요청")
data class AgreeRequest(
    @Schema(description = "동의할 약관 버전 ID 목록", example = "[1, 2, 3]", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "동의할 약관을 하나 이상 선택해주세요")
    val termsVersionIds: List<Long>,
    @Schema(description = "동의 컨텍스트", example = "SIGNUP", requiredMode = Schema.RequiredMode.REQUIRED)
    val contextType: ConsentContextType,
    @Schema(description = "동의 시점 연령대", example = "ADULT", requiredMode = Schema.RequiredMode.REQUIRED)
    val ageGroup: AgeGroup,
    @Schema(description = "이미 발급된 signupSessionId (있으면 재사용)", nullable = true)
    val signupSessionId: UUID? = null,
    @Schema(description = "polymorphic context ID (계약 등)", nullable = true)
    val contextId: Long? = null,
)
