package com.trana.common.exception

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "RFC 7807 ProblemDetail 표준 에러 응답")
data class ProblemDetailResponse(
    @Schema(description = "에러 타입 URI (default: about:blank)")
    val type: String = "about:blank",

    @Schema(description = "ErrorCode 식별자 (예: COMMON_500, AUTH_401, USER_404)")
    val title: String,

    @Schema(description = "HTTP 상태 코드")
    val status: Int,

    @Schema(description = "사용자에게 표시할 상세 메시지")
    val detail: String,

    @Schema(description = "에러 발생 endpoint 경로")
    val instance: String? = null,

    @Schema(description = "클라이언트가 분기 처리할 안정적 식별자 (변경되지 않음)")
    val code: String,

    @Schema(description = "에러 발생 시각 (ISO-8601 UTC)")
    val timestamp: String,

    @Schema(description = "Validation 실패 시 필드별 상세 (그 외 null)")
    val errors: List<ValidationError>? = null,
)

@Schema(description = "Validation 실패 필드 정보")
data class ValidationError(
    @Schema(description = "실패한 필드명")
    val field: String,

    @Schema(description = "실패 사유")
    val message: String,

    @Schema(description = "거부된 입력값")
    val rejectedValue: String? = null,
)
