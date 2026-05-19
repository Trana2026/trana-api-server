package com.trana.terms

internal object ConsentExamples {
    // ───── 요청 예시 ─────

    const val REQUEST_ADULT_SIGNUP = """
            {
              "termsVersionIds": [1, 2, 3],
              "contextType": "SIGNUP",
              "ageGroup": "ADULT"
            }
        """

    const val REQUEST_ADULT_SIGNUP_RESUME = """
            {
              "termsVersionIds": [1, 2, 3],
              "contextType": "SIGNUP",
              "ageGroup": "ADULT",
              "signupSessionId": "20a4b2c9-1f3e-4a7d-9c1b-8e5f2a3b4c5d"
            }
        """

    const val REQUEST_MARKETING_AUTHENTICATED = """
            {
              "termsVersionIds": [4],
              "contextType": "MARKETING",
              "ageGroup": "ADULT"
            }
        """

    const val REQUEST_GUARDIAN_CONSENT = """
          {
            "termsVersionIds": [1, 2, 3],
            "contextType": "SIGNUP",
            "ageGroup": "ADULT",
            "guardianLinkToken": "V1StGXR8_Z5jdHi6B-myT"
          }
      """

    // ───── 응답 예시 ─────

    const val RESPONSE_ADULT_SIGNUP = """
            {
              "signupSessionId": "20a4b2c9-1f3e-4a7d-9c1b-8e5f2a3b4c5d",
              "consents": [
                { "id": 1, "termsVersionId": 1, "agreedAt": "2026-05-19T12:00:00Z" },
                { "id": 2, "termsVersionId": 2, "agreedAt": "2026-05-19T12:00:00Z" },
                { "id": 3, "termsVersionId": 3, "agreedAt": "2026-05-19T12:00:00Z" }
              ]
            }
        """

    const val RESPONSE_AUTHENTICATED = """
            {
              "signupSessionId": null,
              "consents": [
                { "id": 10, "termsVersionId": 4, "agreedAt": "2026-05-19T12:00:00Z" }
              ]
            }
        """

    const val RESPONSE_GUARDIAN_CONSENT = """
          {
            "signupSessionId": null,
            "consents": [
              { "id": 11, "termsVersionId": 1, "agreedAt": "2026-05-19T12:00:00Z" },
              { "id": 12, "termsVersionId": 2, "agreedAt": "2026-05-19T12:00:00Z" },
              { "id": 13, "termsVersionId": 3, "agreedAt": "2026-05-19T12:00:00Z" }
            ]
          }
      """

    // ───── 에러 예시 ─────

    const val TERMS_NOT_FOUND = """
            {
              "type": "about:blank",
              "title": "TERMS_404",
              "status": 404,
              "detail": "약관을 찾을 수 없습니다 (id=99)",
              "code": "TERMS_404",
              "timestamp": "2026-05-19T12:00:00Z"
            }
        """

    const val MINOR_NOT_ALLOWED = """
            {
              "type": "about:blank",
              "title": "COMMON_400",
              "status": 400,
              "detail": "미성년자 본인 동의는 지원하지 않습니다. 보호자 동의 흐름을 사용하세요",
              "code": "COMMON_400",
              "timestamp": "2026-05-19T12:00:00Z"
            }
        """

    const val TOKEN_SESSION_CONFLICT = """
          {
            "type": "about:blank",
            "title": "COMMON_400",
            "status": 400,
            "detail": "signupSessionId 와 guardianLinkToken 은 동시에 사용할 수 없습니다",
            "code": "COMMON_400",
            "timestamp": "2026-05-19T12:00:00Z"
          }
      """

    const val GUARDIAN_LINK_INVALID = """
          {
            "type": "about:blank",
            "title": "GUARDIAN_410_LINK",
            "status": 410,
            "detail": "보호자 인증 링크가 만료되었거나 이미 사용되었습니다",
            "code": "GUARDIAN_410_LINK",
            "timestamp": "2026-05-19T12:00:00Z"
          }
      """
}
