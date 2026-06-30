package com.trana.guardian

internal object GuardianExamples {
    const val LINK_SUCCESS = """
          {
            "token": "V1StGXR8_Z5jdHi6B-myT",
            "expiresAt": "2026-05-22T12:00:00Z",
            "verifyUrl": "https://dev-kyc.trana.kr/verify/V1StGXR8_Z5jdHi6B-myT?openExternalBrowser=1"
          }
      """

    const val NOT_MINOR = """
            {
              "type": "about:blank",
              "title": "GUARDIAN_403_NOT_MINOR",
              "status": 403,
              "detail": "미성년자만 보호자 링크를 발급할 수 있습니다 (userId=1)",
              "code": "GUARDIAN_403_NOT_MINOR",
              "timestamp": "2026-05-19T12:00:00Z"
            }
        """

    const val ALREADY_VERIFIED = """
            {
              "type": "about:blank",
              "title": "GUARDIAN_409_VERIFIED",
              "status": 409,
              "detail": "이미 보호자 인증이 완료된 사용자입니다 (userId=1)",
              "code": "GUARDIAN_409_VERIFIED",
              "timestamp": "2026-05-19T12:00:00Z"
            }
        """

    const val UNAUTHORIZED = """
            {
              "type": "about:blank",
              "title": "AUTH_401",
              "status": 401,
              "detail": "인증이 필요합니다",
              "code": "AUTH_401",
              "timestamp": "2026-05-19T12:00:00Z"
            }
        """
}
