package com.trana.auth

internal object AuthExamples {
    const val SIGN_IN_SUCCESS = """
            {
              "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJ0cmFuYSIsInN1YiI6IjEifQ...",
              "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJ0cmFuYSIsInN1YiI6IjEifQ...",
              "publicCode": "Vh7sK2x9Pq3R"
            }
        """

    const val INVALID_TOKEN = """
            {
              "type": "about:blank",
              "title": "AUTH_401_TOKEN",
              "status": 401,
              "detail": "토큰 검증 실패: JWT expired",
              "code": "AUTH_401_TOKEN",
              "timestamp": "2026-05-07T12:34:56Z"
            }
        """

    const val REQUEST_REFRESH = """
            {
              "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJ0cmFuYSI..."
            }
        """

    const val LOGOUT_WITH_DEVICE_TOKEN = """
          {
            "deviceToken": "dXr8...실제FCM토큰...AB12"
          }
      """

    const val LOGOUT_AUDIT_ONLY = """
          {}
      """
}
