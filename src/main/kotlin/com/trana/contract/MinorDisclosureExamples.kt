package com.trana.contract

internal object MinorDisclosureExamples {
    const val CONFIRM_REQUEST = """
              {
                "disclosedAt": "2026-07-10T12:34:00Z",
                "templateVersion": "v1"
              }
          """

    const val CONFIRM_RESPONSE = """
              {
                "confirmedAt": "2026-07-10T12:34:56Z",
                "templateVersion": "v1"
              }
          """

    const val NOT_CONFIRMED = """
              {
                "type": "about:blank",
                "title": "CONTRACT_403_MINOR_DISCLOSURE",
                "status": 403,
                "detail": "미성년자와 거래 시 서명 전 위험 고지 확인이 필요합니다",
                "code": "CONTRACT_403_MINOR_DISCLOSURE",
                "timestamp": "2026-07-10T12:34:00Z"
              }
          """

    const val NOT_APPLICABLE = """
              {
                "type": "about:blank",
                "title": "CONTRACT_409_MINOR_DISCLOSURE_NA",
                "status": 409,
                "detail": "상대방이 미성년자가 아닌 계약에서는 위험 고지 확인이 불필요합니다",
                "code": "CONTRACT_409_MINOR_DISCLOSURE_NA",
                "timestamp": "2026-07-10T12:34:00Z"
              }
          """

    const val CONTRACT_NOT_FOUND = """
              {
                "type": "about:blank",
                "title": "CONTRACT_404",
                "status": 404,
                "detail": "계약을 찾을 수 없습니다",
                "code": "CONTRACT_404",
                "timestamp": "2026-07-10T12:34:00Z"
              }
          """

    const val UNAUTHORIZED = """
              {
                "type": "about:blank",
                "title": "AUTH_401",
                "status": 401,
                "detail": "인증이 필요합니다",
                "code": "AUTH_401",
                "timestamp": "2026-07-10T12:34:00Z"
              }
          """
}
