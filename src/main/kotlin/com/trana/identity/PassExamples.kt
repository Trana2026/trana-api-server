package com.trana.identity

internal object PassExamples {
    const val REQ_CLIENT_INFO_REQUEST = """
              { "signupSessionId": "20a4b2c9-1f3e-4a7d-9c1b-8e5f2a3b4c5d" }
          """

    const val REQ_CLIENT_INFO_SUCCESS = """
              {
                "serviceId": "7a2d276f-2bd0-4b60-852b-b91da40dd9d3",
                "encryptReqClientInfo": "WBIF9sY4qATKJD+CrxMx3Ml7...cf6z+a5pX+pvebiFmt6ChieUnpZp1pLA==",
                "serviceType": "telcoAuth",
                "usageCode": "01005",
                "retTransferType": "MOKToken",
                "returnUrl": "https://trana-server-dev.up.railway.app/v1/identity/pass/return",
                "encryptVersion": "V2"
              }
          """

    const val SIGNUP_EXPIRED = """
              {
                "type": "about:blank",
                "title": "Gone",
                "status": 410,
                "code": "IDENTITY_410_SIGNUP_EXPIRED",
                "detail": "가입 세션이 만료되었습니다. 약관 동의부터 다시 시작하세요.",
                "timestamp": "2026-06-29T11:00:00.000Z"
              }
          """

    const val RETURN_REDIRECT_DESCRIPTION = """
          302 Found
          Location: https://trana.kr/auth/pass/result#accessToken=eyJ...&refreshToken=eyJ...&publicCode=Vh7sK2x9Pq3R&requiresGuardian=false
      """
}
