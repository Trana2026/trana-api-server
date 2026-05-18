package com.trana.guardian

import com.trana.common.exception.DomainException
import com.trana.common.exception.ErrorCode

/**
 * Guardian 도메인 예외 계층 (sealed class — 같은 파일 내 sub-class만 허용).
 */
sealed class GuardianException(
    errorCode: ErrorCode,
    message: String? = null,
    cause: Throwable? = null,
) : DomainException(errorCode, message, cause) {
    class LinkNotFound(
        token: String,
    ) : GuardianException(
            errorCode = ErrorCode.GUARDIAN_LINK_NOT_FOUND,
            message = "보호자 링크를 찾을 수 없습니다 (token=$token)",
        )

    class LinkInvalid(
        token: String,
    ) : GuardianException(
            errorCode = ErrorCode.GUARDIAN_LINK_INVALID,
            message = "보호자 링크가 만료되었거나 이미 사용/취소되었습니다 (token=$token)",
        )

    class NotMinor(
        userId: Long,
    ) : GuardianException(
            errorCode = ErrorCode.GUARDIAN_NOT_MINOR,
            message = "미성년자만 보호자 링크를 발급할 수 있습니다 (userId=$userId)",
        )

    class AlreadyVerified(
        userId: Long,
    ) : GuardianException(
            errorCode = ErrorCode.GUARDIAN_ALREADY_VERIFIED,
            message = "이미 보호자 인증이 완료된 사용자입니다 (userId=$userId)",
        )

    class NotAdult(
        birthDate: java.time.LocalDate,
    ) : GuardianException(
            errorCode = ErrorCode.GUARDIAN_NOT_ADULT,
            message = "보호자는 성인(만 19세 이상)이어야 합니다 (birthDate=$birthDate)",
        )
}
