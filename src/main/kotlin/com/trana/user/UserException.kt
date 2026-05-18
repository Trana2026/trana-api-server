package com.trana.user

import com.trana.common.exception.DomainException
import com.trana.common.exception.ErrorCode

sealed class UserException(
    errorCode: ErrorCode,
    message: String? = null,
    cause: Throwable? = null,
) : DomainException(errorCode, message, cause) {
    class NotFound(
        identifier: String,
    ) : UserException(
            errorCode = ErrorCode.USER_NOT_FOUND,
            message = "사용자를 찾을 수 없습니다 (identifier=$identifier)",
        )
}
