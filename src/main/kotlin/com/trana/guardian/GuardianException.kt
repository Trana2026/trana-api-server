package com.trana.guardian

import com.trana.common.exception.DomainException
import com.trana.common.exception.ErrorCode

/**
 * 보호자 도메인 예외.
 *
 * - LinkNotFound: token으로 link 조회 실패
 * - LinkInvalid: 만료 또는 이미 사용됨
 * - NotMinor: 성인이 보호자 link 발급 시도
 * - AlreadyVerified: 이미 보호자 인증 완료된 미성년자가 link 재발급 시도
 *
 * 보호자 KYC 자체 실패(NotAdult 등)는 IdentityException (Phase 6).
 */
sealed class GuardianException(
    errorCode: ErrorCode,
    message: String? = null,
    cause: Throwable? = null,
) : DomainException(errorCode, message, cause) {
    class LinkNotFound(
        token: String,
    ) : GuardianException(
            ErrorCode.GUARDIAN_LINK_NOT_FOUND,
            "보호자 링크를 찾을 수 없습니다 (token=${token.take(8)}...)",
        )

    class LinkInvalid(
        token: String,
        reason: String,
    ) : GuardianException(
            ErrorCode.GUARDIAN_LINK_INVALID,
            "보호자 링크 무효: $reason (token=${token.take(8)}...)",
        )

    class NotMinor(
        userId: Long,
    ) : GuardianException(
            ErrorCode.GUARDIAN_NOT_MINOR,
            "미성년자만 보호자 링크를 발급할 수 있습니다 (userId=$userId)",
        )

    class AlreadyVerified(
        userId: Long,
    ) : GuardianException(
            ErrorCode.GUARDIAN_ALREADY_VERIFIED,
            "이미 보호자 인증이 완료된 사용자입니다 (userId=$userId)",
        )
}
