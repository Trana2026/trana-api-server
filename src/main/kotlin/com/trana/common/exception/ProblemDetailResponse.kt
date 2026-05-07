package com.trana.common.exception

import io.swagger.v3.oas.annotations.media.Schema

/**
 * RFC 7807 ProblemDetail 응답 — OpenAPI 문서 전용 모델.
 *
 * 실제 응답은 Spring의 ProblemDetail이 직렬화하지만,
 * 우리 확장 필드(code, timestamp, errors)는 setProperty()로 들어가서
 * OpenAPI 스키마에 자동 인식 안 됨.
 *
 * 이 클래스는 @Schema(implementation = ProblemDetailResponse::class)로
 * 명시해서 프론트에 정확한 응답 구조를 노출하기 위한 문서용 DTO.
 */
@Schema(description = "RFC 7807 ProblemDetail 표준 에러 응답")
data class ProblemDetailResponse(
    @Schema(
        description = "에러 타입 URI (default: about:blank)",
        example = "about:blank",
    )
    val type: String = "about:blank",

    @Schema(
        description = "사람이 읽을 수 있는 짧은 제목",
        example = "USER_404",
    )
    val title: String,

    @Schema(
        description = "HTTP 상태 코드",
        example = "404",
    )
    val status: Int,

    @Schema(
        description = "사용자에게 표시할 상세 메시지",
        example = "사용자를 찾을 수 없습니다 (id=123)",
    )
    val detail: String,

    @Schema(
        description = "에러 발생 endpoint 경로",
        example = "/api/v1/users/me",
    )
    val instance: String? = null,

    @Schema(
        description = "클라이언트가 분기 처리할 안정적 식별자 (변경되지 않음)",
        example = "USER_404",
    )
    val code: String,

    @Schema(
        description = "에러 발생 시각 (ISO-8601 UTC)",
        example = "2026-05-06T12:34:56Z",
    )
    val timestamp: String,

    @Schema(
        description = "Validation 실패 시 필드별 상세 (그 외 null)",
    )
    val errors: List<ValidationError>? = null,
)

@Schema(description = "Validation 실패 필드 정보")
data class ValidationError(
    @Schema(description = "실패한 필드명", example = "email")
    val field: String,

    @Schema(description = "실패 사유", example = "이메일 형식이 올바르지 않습니다")
    val message: String,

    @Schema(description = "거부된 입력값", example = "abc")
    val rejectedValue: String? = null,
)
