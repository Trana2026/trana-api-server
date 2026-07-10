package com.trana.user.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size

@Schema(
    description =
        "본인 정보 수정 요청 — 마이페이지 이메일 / 성별 편집. " +
            "field 없거나 null 이면 변경 없음, 값 있으면 변경 (PATCH partial update)",
)
data class UpdateProfileRequest(
    @field:Email
    @field:Size(max = 255)
    @field:Schema(
        description =
            "새 이메일. null / 미전송 시 변경 없음. " +
                "다른 사용자가 이미 사용 중이면 409 (USER_409_EMAIL_ALREADY_EXISTS)",
        example = "user@example.com",
        nullable = true,
    )
    val email: String? = null,
    @field:Schema(
        description =
            "새 성별. null / 미전송 시 변경 없음. " +
                "MALE=남성 / FEMALE=여성 / NONE=미등록 (user.gender=null 로 저장)",
        example = "MALE",
        nullable = true,
    )
    val gender: UpdateGenderValue? = null,
)

@Schema(
    description =
        "성별 편집용 sentinel enum. 도메인 Gender (MALE/FEMALE/OTHER) 와 별개 — " +
            "NONE 은 '미등록' 상태 (user.gender=null) 를 명시적으로 표현하기 위한 값",
)
enum class UpdateGenderValue {
    MALE,
    FEMALE,
    NONE,
}
