package com.trana.dispute

import com.trana.common.exception.DomainException
import com.trana.common.exception.ErrorCode

/**
 * 분쟁(신고) 도메인 예외.
 *
 * - NotReportable: SIGNED / COMPLETED 외 상태에서 신고 시도
 * - AlreadyActive: 본인이 이미 활성(REPORTED) 신고 보유 — partial UNIQUE 위반 사전 차단
 * - NotFound: 신고 id 매칭 실패
 * - NotReporter: 신고 취소 시 본인이 만든 신고 아님
 *
 * 계약 접근 권한 검증 (참여자 아님) 은 ContractAccessGuard 가 ContractException.NotAccessible 로 처리.
 */
sealed class DisputeException(
    errorCode: ErrorCode,
    message: String? = null,
    cause: Throwable? = null,
) : DomainException(errorCode, message, cause) {
    class NotReportable(
        publicCode: String,
        currentStatus: String,
    ) : DisputeException(
            ErrorCode.DISPUTE_NOT_REPORTABLE,
            "신고 가능 상태가 아닙니다 (publicCode=$publicCode, status=$currentStatus)",
        )

    class AlreadyActive(
        publicCode: String,
        reporterUserId: Long,
    ) : DisputeException(
            ErrorCode.DISPUTE_ALREADY_ACTIVE,
            "이미 활성 신고가 존재합니다 (publicCode=$publicCode, reporterUserId=$reporterUserId)",
        )

    class NotFound(
        disputeId: Long,
    ) : DisputeException(
            ErrorCode.DISPUTE_NOT_FOUND,
            "신고를 찾을 수 없습니다 (disputeId=$disputeId)",
        )

    class NotReporter(
        disputeId: Long,
        userId: Long,
    ) : DisputeException(
            ErrorCode.DISPUTE_NOT_REPORTER,
            "본인이 접수한 신고만 취소할 수 있습니다 (disputeId=$disputeId, userId=$userId)",
        )

    class NoActiveReport(
        publicCode: String,
        userId: Long,
    ) : DisputeException(
            ErrorCode.DISPUTE_NO_ACTIVE_REPORT,
            "활성 신고가 없는 사용자는 증거 패키지를 다운로드할 수 없습니다 (publicCode=$publicCode, userId=$userId)",
        )
}
