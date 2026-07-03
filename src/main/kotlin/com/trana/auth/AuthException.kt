package com.trana.auth

import com.trana.common.exception.DomainException
import com.trana.common.exception.ErrorCode

sealed class AuthException(
    errorCode: ErrorCode,
    message: String? = null,
    cause: Throwable? = null,
) : DomainException(errorCode, message, cause) {
    class InvalidToken(
        detail: String,
        cause: Throwable? = null,
    ) : AuthException(
            errorCode = ErrorCode.INVALID_TOKEN,
            message = "토큰 검증 실패: $detail",
            cause = cause,
        )
}
