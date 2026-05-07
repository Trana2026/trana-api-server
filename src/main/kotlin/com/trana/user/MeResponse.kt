package com.trana.user

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "본인 정보 응답")
data class MeResponse(
    @Schema(
        description = "외부 노출용 사용자 식별자 (nanoid 12자)",
        example = "Vh7sK2x9Pq3R",
    )
    val publicCode: String,

    @Schema(
        description = "이메일 (OAuth 공급자가 제공한 경우)",
        example = "user@example.com",
        nullable = true,
    )
    val email: String?,

    @Schema(
        description = "닉네임 (OAuth 또는 사용자 입력)",
        example = "홍길동",
        nullable = true,
    )
    val nickname: String?,

    @Schema(
        description = "사용자 상태",
        example = "ACTIVE",
    )
    val status: UserStatus,
)
