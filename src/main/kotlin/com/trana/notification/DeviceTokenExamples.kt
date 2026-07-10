package com.trana.notification

internal object DeviceTokenExamples {
    const val REGISTER_REQUEST = """
              {
                "token": "dXr8...실제FCM토큰...AB12",
                "platform": "ANDROID",
                "deviceModel": "iPhone 15 Pro"
              }
          """

    const val REGISTER_RESPONSE = """
              {
                "id": 12
              }
          """

    const val UNREGISTER_REQUEST = """
            {
              "token": "dXr8...실제FCM토큰...AB12"
            }
        """

    const val UNAUTHORIZED = """
              {
                "type": "about:blank",
                "title": "AUTH_401",
                "status": 401,
                "detail": "인증이 필요합니다",
                "code": "AUTH_401",
                "timestamp": "2026-06-16T12:00:00Z"
              }
          """

    const val VALIDATION_FAILED = """
              {
                "type": "about:blank",
                "title": "COMMON_400_INVALID_INPUT",
                "status": 400,
                "detail": "입력값 검증에 실패했습니다",
                "code": "COMMON_400_INVALID_INPUT",
                "timestamp": "2026-06-16T12:00:00Z",
                "errors": [
                  { "field": "token", "message": "must not be blank", "rejectedValue": "" }
                ]
              }
          """

    const val LIST_RESPONSE = """
            [
              {
                "id": 12,
                "platform": "ANDROID",
                "deviceModel": "Samsung Galaxy S24",
                "locationCity": "Seoul",
                "locationCountry": "KR",
                "createdAt": "2026-06-19T12:34:56Z",
                "lastUsedAt": "2026-06-19T13:20:00Z"
              },
              {
                "id": 8,
                "platform": "IOS",
                "deviceModel": "iPhone 15 Pro",
                "locationCity": null,
                "locationCountry": null,
                "createdAt": "2026-06-10T09:00:00Z",
                "lastUsedAt": null
              }
            ]
        """

    const val LIST_EMPTY = """[]"""

    const val PING_REQUEST = """
          {
            "token": "dXr8...실제FCM토큰...AB12"
          }
      """

    const val DEVICE_NOT_FOUND = """
            {
              "type": "about:blank",
              "title": "DEVICE_TOKEN_404",
              "status": 404,
              "detail": "기기를 찾을 수 없습니다 (id=99)",
              "code": "DEVICE_TOKEN_404",
              "timestamp": "2026-06-19T12:00:00Z"
            }
        """
}
