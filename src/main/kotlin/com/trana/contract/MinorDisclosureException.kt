package com.trana.contract

import com.trana.common.exception.DomainException
import com.trana.common.exception.ErrorCode

/**
 * 미성년자 계약 상대방(성인) 위험 고지 확인 도메인 예외.
 *
 * - NotConfirmed: 서명 전 상대측 확인 없음 (서명 endpoint 게이트 실패, 403)
 * - CounterpartyNotMinor: 상대가 미성년자가 아님 → 확인 endpoint 호출 불필요 (409)
 */
sealed class MinorDisclosureException(
    errorCode: ErrorCode,
    message: String? = null,
    cause: Throwable? = null,
) : DomainException(errorCode, message, cause) {
    class NotConfirmed(
        publicCode: String,
    ) : MinorDisclosureException(
            errorCode = ErrorCode.CONTRACT_MINOR_DISCLOSURE_NOT_CONFIRMED,
            message = "미성년자와 거래 시 서명 전 위험 고지 확인이 필요합니다 (publicCode=$publicCode)",
        )

    class CounterpartyNotMinor(
        publicCode: String,
    ) : MinorDisclosureException(
            errorCode = ErrorCode.CONTRACT_MINOR_DISCLOSURE_NOT_APPLICABLE,
            message = "상대방이 미성년자가 아닌 계약에서는 위험 고지 확인이 불필요합니다 (publicCode=$publicCode)",
        )
}
