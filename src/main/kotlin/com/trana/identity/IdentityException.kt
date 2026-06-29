package com.trana.identity

import com.trana.common.exception.DomainException
import com.trana.common.exception.ErrorCode
import java.util.UUID

/**
 * KYC 신원확인 도메인 예외.
 *
 * - SessionNotFound/SessionExpired: id_card_verify_sessions (10분 TTL)
 * - SignupSessionNotFound/SignupSessionExpired: user_consents 매칭 (30분 TTL)
 * - OcrRejected/VerifyRejected/CompareRejected: NCP 비즈니스 검증 실패
 * - VerifyRequired: 단계 순서 위반 (Verify 전 Phone/Compare 호출)
 * - Duplicate: identifier_hash 중복 (이미 가입된 사람의 재가입 시도)
 * - FileInvalid: MIME / 매직바이트 검증 실패
 * - NcpCallFailed: NCP API 통신 자체 실패 (5xx, timeout)
 * - FraudBlocked: PASS 재가입 차단 — fraud_user_hashes.ci_hash 매칭 (PASS-8)
 */
sealed class IdentityException(
    errorCode: ErrorCode,
    message: String? = null,
    cause: Throwable? = null,
) : DomainException(errorCode, message, cause) {
    class SessionNotFound(
        requestId: String,
    ) : IdentityException(
            ErrorCode.IDENTITY_SESSION_NOT_FOUND,
            "Verify 세션을 찾을 수 없습니다 (requestId=$requestId)",
        )

    class SessionExpired(
        requestId: String,
    ) : IdentityException(
            ErrorCode.IDENTITY_SESSION_EXPIRED,
            "Verify 세션이 만료되었습니다 (requestId=$requestId)",
        )

    class SignupSessionNotFound(
        signupSessionId: UUID,
    ) : IdentityException(
            ErrorCode.IDENTITY_SIGNUP_SESSION_NOT_FOUND,
            "가입 세션을 찾을 수 없습니다 (signupSessionId=$signupSessionId)",
        )

    class SignupSessionExpired(
        signupSessionId: UUID,
    ) : IdentityException(
            ErrorCode.IDENTITY_SIGNUP_SESSION_EXPIRED,
            "가입 세션이 만료되었습니다 (signupSessionId=$signupSessionId)",
        )

    class OcrRejected(
        reason: String,
        cause: Throwable? = null,
    ) : IdentityException(
            ErrorCode.IDENTITY_OCR_REJECTED,
            "신분증 OCR 인식 실패: $reason",
            cause,
        )

    class VerifyRejected(
        val ncpErrorCode: String,
        val ncpErrorMessage: String,
    ) : IdentityException(
            ErrorCode.IDENTITY_VERIFY_REJECTED,
            "신분증 진위 확인에 실패했습니다. 사진을 다시 찍어 진행해주세요.",
        ) {
        override val properties: Map<String, Any> =
            mapOf(
                "ncpCode" to ncpErrorCode,
                "ncpMessage" to ncpErrorMessage,
                "hint" to "RETRY_PHOTO",
            )
    }

    class CompareRejected(
        similarity: Double?,
    ) : IdentityException(
            ErrorCode.IDENTITY_COMPARE_REJECTED,
            "얼굴 일치 확인 실패 (similarity=${similarity ?: "-"})",
        )

    class VerifyRequired(
        requestId: String,
    ) : IdentityException(
            ErrorCode.IDENTITY_VERIFY_REQUIRED,
            "Verify 단계 미완료 (requestId=$requestId)",
        )

    class Duplicate(
        identifierHash: String,
    ) : IdentityException(
            ErrorCode.IDENTITY_DUPLICATE,
            "이미 본인인증된 사용자입니다 (hash=${identifierHash.take(8)}...)",
        )

    class FraudBlocked(
        ciHash: String,
    ) : IdentityException(
            ErrorCode.IDENTITY_FRAUD_BLOCKED,
            "사기 신고 확인된 신원으로 가입이 차단됩니다 (ci hash=${ciHash.take(8)}...)",
        )

    class FileInvalid(
        reason: String,
    ) : IdentityException(
            ErrorCode.IDENTITY_FILE_INVALID,
            "신분증 사진 파일 검증 실패: $reason",
        )

    class NcpCallFailed(
        api: String,
        cause: Throwable,
    ) : IdentityException(
            ErrorCode.IDENTITY_NCP_FAILED,
            "NCP $api 호출 실패",
            cause,
        )

    class NotAdult(
        identifierHash: String,
    ) : IdentityException(
            ErrorCode.GUARDIAN_NOT_ADULT,
            "보호자는 성인(만 19세 이상)이어야 합니다 (hash=${identifierHash.take(8)}...)",
        )
}
