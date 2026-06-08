package com.trana.contract

object ContractCancellationExamples {
    const val REQUEST = """
          {
            "reason": "법정대리인 인증을 안한 사람입니다.",
            "detail": "보호자 동의 확인 단계에서 미인증으로 표시되어 안전한 거래 보장이 어렵습니다."
          }
      """

    const val REQUESTED_RESPONSE = """
          {
            "cancellationRequestId": 42,
            "reason": "법정대리인 인증을 안한 사람입니다.",
            "detail": "보호자 동의 확인 단계에서 미인증으로 표시되어 안전한 거래 보장이 어렵습니다.",
            "status": "REQUESTED",
            "requestedAt": "2026-06-08T10:30:00Z",
            "confirmedAt": null,
            "isMine": true
          }
      """

    const val ACTIVE_RESPONSE = """
          {
            "cancellationRequestId": 42,
            "reason": "법정대리인 인증을 안한 사람입니다.",
            "detail": "보호자 동의 확인 단계에서 미인증으로 표시되어 안전한 거래 보장이 어렵습니다.",
            "status": "REQUESTED",
            "requestedAt": "2026-06-08T10:30:00Z",
            "confirmedAt": null,
            "isMine": false
          }
      """

    const val NOT_REQUESTABLE = """
          {
            "type": "about:blank",
            "title": "Conflict",
            "status": 409,
            "detail": "취소 요청 가능 상태가 아닙니다 (publicCode=Ab12cdEFgH7x, status=DRAFT)",
            "code": "CONTRACT_CANCELLATION_409_NOT_REQUESTABLE",
            "timestamp": "2026-06-08T10:30:00Z"
          }
      """

    const val NOT_ELIGIBLE_REQUESTER = """
          {
            "type": "about:blank",
            "title": "Forbidden",
            "status": 403,
            "detail": "서명 요청을 받은 측만 취소 요청할 수 있습니다 (publicCode=Ab12cdEFgH7x, userId=10)",
            "code": "CONTRACT_CANCELLATION_403_NOT_ELIGIBLE_REQUESTER",
            "timestamp": "2026-06-08T10:30:00Z"
          }
      """

    const val ALREADY_ACTIVE = """
          {
            "type": "about:blank",
            "title": "Conflict",
            "status": 409,
            "detail": "이미 활성 취소 요청이 존재합니다 (publicCode=Ab12cdEFgH7x)",
            "code": "CONTRACT_CANCELLATION_409_ALREADY_ACTIVE",
            "timestamp": "2026-06-08T10:30:00Z"
          }
      """

    const val NOT_FOUND = """
          {
            "type": "about:blank",
            "title": "Not Found",
            "status": 404,
            "detail": "활성 취소 요청을 찾을 수 없습니다 (publicCode=Ab12cdEFgH7x)",
            "code": "CONTRACT_CANCELLATION_404",
            "timestamp": "2026-06-08T10:30:00Z"
          }
      """

    const val SELF_CONFIRM = """
          {
            "type": "about:blank",
            "title": "Forbidden",
            "status": 403,
            "detail": "취소 요청자 본인은 자기 요청을 확정할 수 없습니다 (publicCode=Ab12cdEFgH7x, userId=10)",
            "code": "CONTRACT_CANCELLATION_403_SELF_CONFIRM",
            "timestamp": "2026-06-08T10:30:00Z"
          }
      """

    const val NOT_ACCESSIBLE = """
          {
            "type": "about:blank",
            "title": "Forbidden",
            "status": 403,
            "detail": "이 계약에 접근할 권한이 없습니다 (publicCode=Ab12cdEFgH7x, userId=99)",
            "code": "CONTRACT_403_NOT_ACCESSIBLE",
            "timestamp": "2026-06-08T10:30:00Z"
          }
      """
}
