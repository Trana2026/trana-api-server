package com.trana.user

object UserExamples {
    const val ME_ADULT = """
  {
    "publicCode": "Vh7sK2x9Pq3R",
    "email": null,
    "status": "ACTIVE",
    "ageGroup": "ADULT",
    "guardianVerifiedAt": null,
    "name": "김테스트",
    "birthDate": "1990-01-01",
    "gender": "MALE",
    "phone": "010-1234-5678",
    "pushEnabled": true
  }
  """

    const val ME_MINOR = """
  {
    "publicCode": "Mn4kL9w2Qp7T",
    "email": "min-c@kakao.local",
    "status": "ACTIVE",
    "ageGroup": "MINOR",
    "guardianVerifiedAt": "2026-06-10T12:34:56Z",
    "name": "미성년C",
    "birthDate": null,
    "gender": null,
    "phone": null,
    "pushEnabled": true
  }
  """

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
