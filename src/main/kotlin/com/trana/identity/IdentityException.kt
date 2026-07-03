package com.trana.identity

import com.trana.common.exception.DomainException
import com.trana.common.exception.ErrorCode
import java.util.UUID

/**
 * PASS 신원확인 도메인 예외.
 *
 * - SignupSessionNotFound/SignupSessionExpired: user_consents 매칭 (30분 TTL)
 * - FraudBlocked: PASS 재가입 차단 — fraud_user_hashes.ci_hash 매칭 (PASS-8)
 */
sealed class IdentityException(
    errorCode: ErrorCode,
    message: String? = null,
    cause: Throwable? = null,
) : DomainException(errorCode, message, cause) {
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

    class FraudBlocked(
        ciHash: String,
    ) : IdentityException(
            ErrorCode.IDENTITY_FRAUD_BLOCKED,
            "사기 신고 확인된 신원으로 가입이 차단됩니다 (ci hash=${ciHash.take(8)}...)",
        )
}
