package com.trana.contract

import com.trana.common.exception.DomainException
import com.trana.common.exception.ErrorCode

/**
 * 계약 취소 요청 도메인 예외 (W7).
 *
 * - NotRequestable: SHARED / RECEIVER_SIGNED 외 상태에서 요청 시도
 * - NotEligibleRequester: 서명 요청 송신 측 (SHARED 의 creator / RECEIVER_SIGNED 의 receiver) 이 시도
 * - AlreadyActive: 한 계약에 활성(REQUESTED) 요청 1건 이미 존재
 * - NotFound: 활성 요청 없는데 confirm 시도
 * - SelfConfirm: 요청자 본인이 자기 요청 confirm 시도
 *
 * 계약 접근 권한 (참여자 아님) 은 ContractAccessGuard 가 ContractException.NotAccessible 로 처리.
 */
sealed class ContractCancellationException(
    errorCode: ErrorCode,
    message: String? = null,
    cause: Throwable? = null,
) : DomainException(errorCode, message, cause) {
    class NotRequestable(
        publicCode: String,
        currentStatus: String,
    ) : ContractCancellationException(
            ErrorCode.CONTRACT_CANCELLATION_NOT_REQUESTABLE,
            "취소 요청 가능 상태가 아닙니다 (publicCode=$publicCode, status=$currentStatus)",
        )

    class NotEligibleRequester(
        publicCode: String,
        userId: Long,
    ) : ContractCancellationException(
            ErrorCode.CONTRACT_CANCELLATION_NOT_ELIGIBLE_REQUESTER,
            "서명 요청을 받은 측만 취소 요청할 수 있습니다 (publicCode=$publicCode, userId=$userId)",
        )

    class AlreadyActive(
        publicCode: String,
    ) : ContractCancellationException(
            ErrorCode.CONTRACT_CANCELLATION_ALREADY_ACTIVE,
            "이미 활성 취소 요청이 존재합니다 (publicCode=$publicCode)",
        )

    class NotFound(
        publicCode: String,
    ) : ContractCancellationException(
            ErrorCode.CONTRACT_CANCELLATION_NOT_FOUND,
            "활성 취소 요청을 찾을 수 없습니다 (publicCode=$publicCode)",
        )

    class SelfConfirm(
        publicCode: String,
        userId: Long,
    ) : ContractCancellationException(
            ErrorCode.CONTRACT_CANCELLATION_SELF_CONFIRM,
            "취소 요청자 본인은 자기 요청을 확정할 수 없습니다 (publicCode=$publicCode, userId=$userId)",
        )
}
