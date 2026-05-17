package com.trana.identity

import com.trana.common.exception.DomainException
import com.trana.common.exception.ErrorCode

/**
 * Identity 도메인 예외 계층.
 *
 * sealed class로 같은 패키지 내 sub-class만 허용 → 컴파일 타임 안전.
 * 새 예외 종류 추가 시 이 파일에 sub-class 추가.
 */
sealed class IdentityException(
    errorCode: ErrorCode,
    message: String? = null,
    cause: Throwable? = null,
) : DomainException(errorCode, message, cause) {
    class SessionNotFound(
        requestId: String,
    ) : IdentityException(
            errorCode = ErrorCode.IDENTITY_SESSION_NOT_FOUND,
            message = "Verify 세션을 찾을 수 없습니다 (requestId=$requestId)",
        )

    class SessionExpired(
        requestId: String,
    ) : IdentityException(
            errorCode = ErrorCode.IDENTITY_SESSION_EXPIRED,
            message = "Verify 세션이 만료되었습니다 (requestId=$requestId)",
        )
}
