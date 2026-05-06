package com.trana.common.exception

import org.springframework.http.HttpStatus

/**
 * 에러 코드 표준 정의.
 *
 * - status: HTTP 응답 상태
 * - code: 클라이언트가 분기처리할 안정적 식별자 (변경 금지, 운영 호환성)
 * - message: 사용자에게 보여줄 기본 메시지 (i18n 도입 시 키로 전환 가능)
 *
 * 명명 규칙: {도메인}_{HTTP상태}[_{서브식별자}]
 * 도메인 enum 값은 도메인 도입 시점에 추가.
 */
enum class ErrorCode(val status: HttpStatus, val code: String, val message: String) {
    // === 공통 (COMMON_*) ===
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 오류가 발생했습니다"),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_400", "잘못된 입력입니다"),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "COMMON_400_BODY", "요청 본문을 파싱할 수 없습니다"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_404", "요청한 리소스를 찾을 수 없습니다"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_405", "허용되지 않은 메서드입니다"),

    // === 인증 (AUTH_*) — W2 도입 시 활성화 ===
    // UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_401", "인증이 필요합니다"),
    // FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_403", "권한이 없습니다"),
    // INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401_TOKEN", "유효하지 않은 토큰입니다"),

    // === 사용자 (USER_*) — W2 도입 시 ===
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404", "사용자를 찾을 수 없습니다"),
}
