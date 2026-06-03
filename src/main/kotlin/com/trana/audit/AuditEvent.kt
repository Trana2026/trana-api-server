package com.trana.audit

/**
 * Audit 이벤트 타입 (refactor kk).
 *
 * - WORM (insert-only) `audit_logs.event_type` 컬럼 값
 * - 매직 문자열 자유 입력 차단 → 오탈자 검출 + 검색 일관성 + 컴파일 타임 검증
 * - 도메인 prefix 일관 유지 (USER_ / IDENTITY_ / GUARDIAN_ / CONSENT_)
 */
enum class AuditEvent {
    // User 도메인
    USER_CREATED,
    USER_WITHDRAWN,

    // Identity 도메인 — 본인 KYC
    IDENTITY_OCR_PASSED,
    IDENTITY_VERIFY_PASSED,
    IDENTITY_VERIFY_FAILED,
    IDENTITY_PHONE_RECORDED,
    IDENTITY_COMPARE_FAILED,
    IDENTITY_SIGNUP_COMPLETED,
    SIGNUP_KYC_CANCEL_NOOP,
    SIGNUP_KYC_CANCELED,

    // Identity 도메인 — 보호자 KYC
    GUARDIAN_IDENTITY_OCR_PASSED,
    GUARDIAN_IDENTITY_COMPARE_FAILED,
    GUARDIAN_VERIFIED_COMPLETED,

    // Guardian 도메인
    GUARDIAN_LINK_CREATED,
    CONTRACT_GUARDIAN_LINK_CREATED,

    // Consent (약관 동의)
    CONSENT_AGREED,
}
