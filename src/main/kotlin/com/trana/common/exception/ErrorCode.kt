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
enum class ErrorCode(
    val status: HttpStatus,
    val code: String,
    val message: String,
) {
    // === 공통 (COMMON_*) ===
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 오류가 발생했습니다"),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_400", "잘못된 입력입니다"),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "COMMON_400_BODY", "요청 본문을 파싱할 수 없습니다"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_404", "요청한 리소스를 찾을 수 없습니다"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_405", "허용되지 않은 메서드입니다"),

    // === 인증 (AUTH_*) — W2 도입 시 활성화 ===
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_401", "인증이 필요합니다"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401_TOKEN", "유효하지 않은 토큰입니다"),
    INVALID_SOCIAL_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401_SOCIAL", "소셜 공급자 토큰 검증 실패"),
    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "AUTH_400_PROVIDER", "지원하지 않는 소셜 공급자입니다"),

    // === 사용자 (USER_*) — W2 도입 시 ===
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404", "사용자를 찾을 수 없습니다"),
    USER_ALREADY_WITHDRAWN(HttpStatus.CONFLICT, "USER_409_ALREADY_WITHDRAWN", "이미 탈퇴한 사용자입니다"),

    // === 약관 (TERMS_*) ===
    TERMS_NOT_FOUND(HttpStatus.NOT_FOUND, "TERMS_404", "약관을 찾을 수 없습니다"),

    // === KYC 신원확인 (IDENTITY_*) ===
    IDENTITY_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "IDENTITY_404_SESSION", "Verify 세션을 찾을 수 없습니다 (OCR을 다시 진행해주세요)"),
    IDENTITY_SESSION_EXPIRED(HttpStatus.GONE, "IDENTITY_410_SESSION", "Verify 세션이 만료되었습니다 (OCR을 다시 진행해주세요)"),
    IDENTITY_SIGNUP_SESSION_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "IDENTITY_404_SIGNUP",
        "가입 세션을 찾을 수 없습니다 (약관 동의부터 다시 진행해주세요)",
    ),
    IDENTITY_SIGNUP_SESSION_EXPIRED(HttpStatus.GONE, "IDENTITY_410_SIGNUP", "가입 세션이 만료되었습니다 (약관 동의부터 다시 진행해주세요)"),
    IDENTITY_OCR_REJECTED(HttpStatus.UNPROCESSABLE_ENTITY, "IDENTITY_422_OCR", "신분증 OCR 인식에 실패했습니다"),
    IDENTITY_VERIFY_REJECTED(HttpStatus.UNPROCESSABLE_ENTITY, "IDENTITY_422_VERIFY", "신분증 진위확인에 실패했습니다"),
    IDENTITY_COMPARE_REJECTED(HttpStatus.UNPROCESSABLE_ENTITY, "IDENTITY_422_COMPARE", "얼굴 일치 확인에 실패했습니다"),
    IDENTITY_VERIFY_REQUIRED(HttpStatus.CONFLICT, "IDENTITY_409_VERIFY_REQUIRED", "신분증 진위확인을 먼저 완료해주세요"),
    IDENTITY_DUPLICATE(HttpStatus.CONFLICT, "IDENTITY_409_DUPLICATE", "이미 본인인증된 사용자입니다"),
    IDENTITY_FILE_INVALID(HttpStatus.BAD_REQUEST, "IDENTITY_400_FILE", "신분증 사진 파일이 유효하지 않습니다"),
    IDENTITY_NCP_FAILED(HttpStatus.BAD_GATEWAY, "IDENTITY_502_NCP", "신원확인 외부 서비스 통신에 실패했습니다"),

    // === 보호자 (GUARDIAN_*) ===
    GUARDIAN_LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "GUARDIAN_404_LINK", "보호자 링크를 찾을 수 없습니다"),
    GUARDIAN_LINK_INVALID(HttpStatus.GONE, "GUARDIAN_410_LINK", "보호자 링크가 만료되었거나 이미 사용/취소되었습니다"),
    GUARDIAN_NOT_MINOR(HttpStatus.FORBIDDEN, "GUARDIAN_403_NOT_MINOR", "미성년자만 보호자 링크를 발급할 수 있습니다"),
    GUARDIAN_ALREADY_VERIFIED(HttpStatus.CONFLICT, "GUARDIAN_409_VERIFIED", "이미 보호자 인증이 완료된 사용자입니다"),
    GUARDIAN_NOT_ADULT(HttpStatus.FORBIDDEN, "GUARDIAN_403_NOT_ADULT", "보호자는 성인(만 19세 이상)이어야 합니다"),

    // === 계약 (CONTRACT_*) — W4 도입 ===
    CONTRACT_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTRACT_404", "계약을 찾을 수 없습니다"),
    CONTRACT_NOT_OWNER(HttpStatus.FORBIDDEN, "CONTRACT_403_OWNER", "본인이 작성한 계약만 수정할 수 있습니다"),
    CONTRACT_NOT_DRAFT(HttpStatus.CONFLICT, "CONTRACT_409_NOT_DRAFT", "DRAFT 상태에서만 수정/삭제할 수 있습니다"),
    CONTRACT_ALREADY_DELETED(HttpStatus.GONE, "CONTRACT_410_DELETED", "이미 삭제된 계약입니다"),
    CONTRACT_MAX_ATTACHMENTS(HttpStatus.CONFLICT, "CONTRACT_409_ATTACHMENTS", "사진은 최대 7장까지 업로드할 수 있습니다"),
    CONTRACT_ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTRACT_404_ATTACHMENT", "첨부 파일을 찾을 수 없습니다"),
    CONTRACT_INVALID_CONSENT_TYPE(HttpStatus.BAD_REQUEST, "CONTRACT_400_CONSENT", "계약 생성 시 보호자 동의 유형이 올바르지 않습니다"),
    CONTRACT_GUARDIAN_CONSENT_REQUIRED(HttpStatus.CONFLICT, "CONTRACT_409_GUARDIAN_REQUIRED", "보호자 동의가 완료되지 않은 계약입니다"),
    CONTRACT_GUARDIAN_CONSENT_ALREADY(HttpStatus.CONFLICT, "CONTRACT_409_GUARDIAN_ALREADY", "이미 보호자 동의가 완료된 계약입니다"),
    CONTRACT_AI_EXTRACTION_FAILED(HttpStatus.BAD_GATEWAY, "CONTRACT_502_AI", "AI 추출 호출에 실패했습니다"),
    CONTRACT_AI_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "CONTRACT_502_AI_PARSE", "AI 응답을 파싱할 수 없습니다"),
    CONTRACT_NOT_READY_ELIGIBLE(
        HttpStatus.BAD_REQUEST,
        "CONTRACT_400_NOT_READY",
        "READY 전이 조건 미충족 (필수 필드 누락)",
    ),
    CONTRACT_NOT_IN_READY_STATE(
        HttpStatus.CONFLICT,
        "CONTRACT_409_NOT_READY",
        "현재 READY 상태가 아닙니다",
    ),
    CONTRACT_PDF_NOT_GENERATED(
        HttpStatus.CONFLICT,
        "CONTRACT_409_PDF_NOT_GENERATED",
        "PDF 가 아직 생성되지 않은 계약입니다 (markReady 가 선행 필요)",
    ),
    CONTRACT_AI_IMAGE_COUNT_INVALID(
        HttpStatus.BAD_REQUEST,
        "CONTRACT_400_AI_IMAGE_COUNT",
        "AI 분석은 1~2장의 사진만 가능합니다",
    ),
    CONTRACT_AI_EXTRACTION_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "CONTRACT_404_AI_EXTRACTION",
        "AI 추출 결과를 찾을 수 없습니다",
    ),
}
