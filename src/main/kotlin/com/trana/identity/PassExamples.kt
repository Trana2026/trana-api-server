package com.trana.identity

internal object PassExamples {
    const val REQ_CLIENT_INFO_SUCCESS = """
              {
                "serviceId": "7a2d276f-2bd0-4b60-852b-b91da40dd9d3",
                "encryptReqClientInfo": "WBIF9sY4qATKJD+CrxMx3Ml7...cf6z+a5pX+pvebiFmt6ChieUnpZp1pLA==",
                "serviceType": "telcoAuth",
                "usageCode": "01005",
                "retTransferType": "MOKToken",
                "returnUrl": "https://dev-api.trana.kr/v1/identity/pass/return",
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
          Location: https://dev-kyc.trana.kr/auth/pass/result#accessToken=eyJ...&refreshToken=eyJ...&publicCode=Vh7sK2x9Pq3R&requiresGuardian=false
      """

    const val GUARDIAN_LINK_NOT_FOUND = """
              {
                "type": "about:blank",
                "title": "Not Found",
                "status": 404,
                "code": "GUARDIAN_404_LINK_NOT_FOUND",
                "detail": "보호자 링크를 찾을 수 없습니다.",
                "timestamp": "2026-06-29T11:00:00.000Z"
              }
          """

    const val GUARDIAN_LINK_INVALID = """
              {
                "type": "about:blank",
                "title": "Gone",
                "status": 410,
                "code": "GUARDIAN_410_LINK_INVALID",
                "detail": "보호자 링크가 만료되었거나 이미 사용되었습니다.",
                "timestamp": "2026-06-29T11:00:00.000Z"
              }
          """
}
