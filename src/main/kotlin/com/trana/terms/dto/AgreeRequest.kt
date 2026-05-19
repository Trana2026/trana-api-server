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
    @Schema(
        description = """
이미 발급된 signupSessionId. 비인증 성인 가입 흐름에서만 의미.
- 첫 호출: null/생략 → 서버가 새 UUID 발급
- 추가 약관 동의: 이전 응답의 signupSessionId 그대로 재사용 → 같은 세션 누적
- 인증 사용자: 무시 (응답 signupSessionId=null)
  """,
        nullable = true,
        example = "20a4b2c9-1f3e-4a7d-9c1b-8e5f2a3b4c5d",
    )
    val signupSessionId: UUID? = null,
    @Schema(description = "polymorphic context ID (계약 등)", nullable = true)
    val contextId: Long? = null,
)
