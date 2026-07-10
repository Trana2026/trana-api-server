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

    class AlreadyWithdrawn(
        userId: Long,
    ) : UserException(
            errorCode = ErrorCode.USER_ALREADY_WITHDRAWN,
            message = "이미 탈퇴한 사용자입니다 (userId=$userId)",
        )

    class EmailAlreadyExists(
        email: String,
    ) : UserException(
            errorCode = ErrorCode.USER_EMAIL_ALREADY_EXISTS,
            message = "이미 사용 중인 이메일입니다 (email=$email)",
        )

    class InquiryNotFound(
        publicCode: String,
    ) : UserException(
            errorCode = ErrorCode.INQUIRY_NOT_FOUND,
            message = "문의를 찾을 수 없습니다 (publicCode=$publicCode)",
        )
}
