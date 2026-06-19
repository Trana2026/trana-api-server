package com.trana.notification

import com.trana.common.exception.DomainException
import com.trana.common.exception.ErrorCode

sealed class DeviceTokenException(
    errorCode: ErrorCode,
    message: String? = null,
    cause: Throwable? = null,
) : DomainException(errorCode, message, cause) {
    class NotFound(
        deviceTokenId: Long,
    ) : DeviceTokenException(
            errorCode = ErrorCode.DEVICE_TOKEN_NOT_FOUND,
            message = "기기를 찾을 수 없습니다 (id=$deviceTokenId)",
        )
}
