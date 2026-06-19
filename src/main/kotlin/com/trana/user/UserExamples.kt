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
}
