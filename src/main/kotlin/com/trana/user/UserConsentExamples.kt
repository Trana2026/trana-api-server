package com.trana.user

internal object UserConsentExamples {
    const val MY_CONSENTS_ADULT = """
  [
    {
      "termsId": 5,
      "type": "SERVICE",
      "version": "1.0.0",
      "title": "서비스 이용약관",
      "agreedAt": "2026-06-19T12:34:56Z"
    },
    {
      "termsId": 6,
      "type": "PRIVACY",
      "version": "1.0.0",
      "title": "개인정보 처리방침",
      "agreedAt": "2026-06-19T12:34:56Z"
    },
    {
      "termsId": 7,
      "type": "MARKETING",
      "version": "1.0.0",
      "title": "마케팅 정보 수신 동의",
      "agreedAt": "2026-06-19T12:34:56Z"
    }
  ]
  """

    const val MY_CONSENTS_EMPTY = """[]"""
}
