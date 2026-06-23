package com.trana.trustscore

import com.trana.common.exception.DomainException
import com.trana.common.exception.ErrorCode

sealed class TrustScoreException(
    errorCode: ErrorCode,
    message: String? = null,
    cause: Throwable? = null,
) : DomainException(errorCode, message, cause) {
    /**
     * 면제 티켓 사용 시도했으나 보유 티켓이 없음.
     * 명세 E03 — "보유한 면제 티켓이 없습니다" (3초 토스트, 결제 화면 유지).
     */
    class NoUnusedTicket(
        userId: Long,
    ) : TrustScoreException(
            errorCode = ErrorCode.TRUST_SCORE_NO_UNUSED_TICKET,
            message = "보유한 면제 티켓이 없습니다 (userId=$userId)",
        )
}
