package com.trana.notification

import com.trana.common.exception.DomainException
import com.trana.common.exception.ErrorCode

sealed class NotificationException(
    errorCode: ErrorCode,
    message: String? = null,
    cause: Throwable? = null,
) : DomainException(errorCode, message, cause) {
    /**
     * 존재하지 않거나 본인 소유가 아닌 알림.
     * 소유 여부 노출 방지 위해 Forbidden 대신 NotFound 로 통일 (repo.findByIdAndUserId 실패 시).
     */
    class NotFound(
        notificationId: Long,
    ) : NotificationException(
            errorCode = ErrorCode.NOTIFICATION_NOT_FOUND,
            message = "알림을 찾을 수 없습니다 (id=$notificationId)",
        )
}
