package com.trana.notification

internal object NotificationExamples {
    const val LIST_RESPONSE = """
              {
                "content": [
                  {
                    "id": 42,
                    "category": "CONTRACT",
                    "title": "새 계약서 도착",
                    "body": "이테스트B님이 서명을 요청했어요",
                    "deepLink": "trana://contracts/CT-XXXX-01",
                    "isRead": false,
                    "readAt": null,
                    "createdAt": "2026-07-10T02:50:00Z"
                  },
                  {
                    "id": 41,
                    "category": "CONTRACT",
                    "title": "계약 체결 완료",
                    "body": "양측 서명이 완료되어 계약이 체결됐어요",
                    "deepLink": "trana://contracts/CT-ZZZZ-03",
                    "isRead": true,
                    "readAt": "2026-07-07T03:00:00Z",
                    "createdAt": "2026-07-05T03:00:00Z"
                  }
                ],
                "page": 0,
                "size": 20,
                "totalElements": 4,
                "totalPages": 1,
                "hasNext": false
              }
          """

    const val LIST_EMPTY = """
              {
                "content": [],
                "page": 0,
                "size": 20,
                "totalElements": 0,
                "totalPages": 0,
                "hasNext": false
              }
          """

    const val UNAUTHORIZED = """
              {
                "type": "about:blank",
                "title": "AUTH_401",
                "status": 401,
                "detail": "인증이 필요합니다",
                "code": "AUTH_401",
                "timestamp": "2026-07-10T03:00:00Z"
              }
          """

    const val NOT_FOUND = """
              {
                "type": "about:blank",
                "title": "NOTIFICATION_404",
                "status": 404,
                "detail": "알림을 찾을 수 없습니다 (id=99)",
                "code": "NOTIFICATION_404",
                "timestamp": "2026-07-10T03:00:00Z"
              }
          """

    const val VALIDATION_FAILED = """
              {
                "type": "about:blank",
                "title": "COMMON_400_INVALID_INPUT",
                "status": 400,
                "detail": "입력값 검증에 실패했습니다",
                "code": "COMMON_400_INVALID_INPUT",
                "timestamp": "2026-07-10T03:00:00Z",
                "errors": [
                  { "field": "size", "message": "must be less than or equal to 50", "rejectedValue": "100" }
                ]
              }
          """
}
