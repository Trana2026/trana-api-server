package com.trana.notification

internal object DeviceTokenExamples {
    const val REGISTER_REQUEST = """
            {
              "token": "dXr8...실제FCM토큰...AB12",
              "platform": "ANDROID"
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
}
