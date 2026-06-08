package com.trana.dispute

/**
 * Swagger UI 의 @ExampleObject 에 사용되는 JSON 상수.
 * 도메인별 분리 — ContractExamples 와 별도 (refactor b 패턴).
 */
object DisputeExamples {
    const val REPORT_REQUEST = """
          {
            "reason": "물건 상태가 설명과 다름",
            "detail": "사진과 달리 액정 멍이 있고 배터리 효율 70%대. 환불 요청 했으나 응답 없음."
          }
      """

    const val REPORTED_RESPONSE = """
          {
            "disputeId": 42,
            "reason": "물건 상태가 설명과 다름",
            "detail": "사진과 달리 액정 멍이 있고 배터리 효율 70%대. 환불 요청 했으나 응답 없음.",
            "status": "REPORTED",
            "reportedAt": "2026-06-08T10:30:00Z",
            "cancelledAt": null,
            "isMine": true
          }
      """

    const val LIST_RESPONSE = """
          {
            "disputes": [
              {
                "disputeId": 42,
                "reason": "물건 상태가 설명과 다름",
                "detail": "사진과 달리 액정 멍이 있음...",
                "status": "REPORTED",
                "reportedAt": "2026-06-08T10:30:00Z",
                "cancelledAt": null,
                "isMine": true
              }
            ]
          }
      """

    const val NOT_REPORTABLE = """
          {
            "type": "about:blank",
            "title": "Conflict",
            "status": 409,
            "detail": "신고 가능 상태가 아닙니다 (publicCode=Ab12cdEFgH7x, status=SHARED)",
            "code": "DISPUTE_409_NOT_REPORTABLE",
            "timestamp": "2026-06-08T10:30:00Z"
          }
      """

    const val ALREADY_ACTIVE = """
          {
            "type": "about:blank",
            "title": "Conflict",
            "status": 409,
            "detail": "이미 활성 신고가 존재합니다 (publicCode=Ab12cdEFgH7x, reporterUserId=10)",
            "code": "DISPUTE_409_ALREADY_ACTIVE",
            "timestamp": "2026-06-08T10:30:00Z"
          }
      """

    const val NOT_FOUND = """
          {
            "type": "about:blank",
            "title": "Not Found",
            "status": 404,
            "detail": "신고를 찾을 수 없습니다 (disputeId=42)",
            "code": "DISPUTE_404",
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

    const val NO_ACTIVE_REPORT = """
      {
        "type": "about:blank",
        "title": "Forbidden",
        "status": 403,
        "detail": "활성 신고가 없는 사용자는 증거 패키지를 다운로드할 수 없습니다 (publicCode=Ab12cdEFgH7x, userId=10)",
        "code": "DISPUTE_403_NO_ACTIVE_REPORT",
        "timestamp": "2026-06-08T10:30:00Z"
      }
  """
}
