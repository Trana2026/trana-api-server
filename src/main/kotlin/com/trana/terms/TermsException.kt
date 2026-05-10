package com.trana.terms

import com.trana.common.exception.DomainException
import com.trana.common.exception.ErrorCode

sealed class TermsException(
    errorCode: ErrorCode,
    message: String? = null,
    cause: Throwable? = null,
) : DomainException(errorCode, message, cause) {
    class NotFound(
        termsVersionId: Long,
    ) : TermsException(
            errorCode = ErrorCode.TERMS_NOT_FOUND,
            message = "약관을 찾을 수 없습니다 (id=$termsVersionId)",
        )
}
