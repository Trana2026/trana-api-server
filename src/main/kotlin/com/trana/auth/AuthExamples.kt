package com.trana.auth

internal object AuthExamples {
    const val SIGN_IN_SUCCESS = """
          {
            "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJ0cmFuYSIsInN1YiI6IjEifQ...",
            "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJ0cmFuYSIsInN1YiI6IjEifQ...",
            "publicCode": "Vh7sK2x9Pq3R"
          }
      """

    const val INVALID_SOCIAL_TOKEN = """
          {
            "type": "about:blank",
            "title": "AUTH_401_SOCIAL",
            "status": 401,
            "detail": "KAKAO 토큰 검증 실패",
            "code": "AUTH_401_SOCIAL",
            "timestamp": "2026-05-07T12:34:56Z"
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

    const val INVALID_BODY = """
          {
            "type": "about:blank",
            "title": "COMMON_400_BODY",
            "status": 400,
            "detail": "요청 본문을 파싱할 수 없습니다",
            "code": "COMMON_400_BODY",
            "timestamp": "2026-05-07T12:34:56Z"
          }
      """

    const val REQUEST_KAKAO = """
          {
            "provider": "KAKAO",
            "accessToken": "kakao_access_token_here"
          }
      """

    const val REQUEST_GOOGLE = """
          {
            "provider": "GOOGLE",
            "accessToken": "google_access_token_here"
          }
      """

    const val REQUEST_REFRESH = """
          {
            "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJ0cmFuYSI..."
          }
      """
}
