package com.trana.user

object UserInquiryExamples {
    const val CREATE_REQUEST = """
  {
    "email": "user@example.com",
    "title": "보호자 동의 절차 문의",
    "content": "미성년자 가입 후 보호자 동의 링크를 받았는데, 보호자분이 링크를 열어도 페이지가 표시되지 않는다고 합니다.\n\n다시 보내려면 어떻게 해야 하나요?"
  }
  """

    const val CREATE_RESPONSE = """
  {
    "publicCode": "Iq7sK2x9Pq3R",
    "title": "보호자 동의 절차 문의",
    "createdAt": "2026-06-19T12:34:56Z"
  }
  """

    const val LIST_RESPONSE = """
  [
    {
      "publicCode": "Iq7sK2x9Pq3R",
      "title": "보호자 동의 절차 문의",
      "createdAt": "2026-06-19T12:34:56Z"
    },
    {
      "publicCode": "Bn3wK8x1Lp9M",
      "title": "계약 공유 알림톡 안 옴",
      "createdAt": "2026-06-15T09:00:00Z"
    }
  ]
  """

    const val LIST_EMPTY = """[]"""

    const val DETAIL_RESPONSE = """
  {
    "publicCode": "Iq7sK2x9Pq3R",
    "email": "user@example.com",
    "title": "보호자 동의 절차 문의",
    "content": "미성년자 가입 후 보호자 동의 링크를 받았는데, 보호자분이 링크를 열어도 페이지가 표시되지 않는다고 합니다.\n\n다시 보내려면 어떻게 해야 하나요?",
    "createdAt": "2026-06-19T12:34:56Z"
  }
  """

    const val VALIDATION_FAILED = """
  {
    "type": "about:blank",
    "title": "Bad Request",
    "status": 400,
    "detail": "Validation failed",
    "code": "VALIDATION_FAILED",
    "errors": [
      {"field": "email", "message": "올바른 이메일 형식이 아닙니다"},
      {"field": "content", "message": "크기는 1에서 2000 사이여야 합니다"}
    ]
  }
  """

    const val NOT_FOUND = """
  {
    "type": "about:blank",
    "title": "Not Found",
    "status": 404,
    "detail": "문의를 찾을 수 없습니다 (publicCode=Xxxxxxxxxxxx)",
    "code": "INQUIRY_404"
  }
  """
}
