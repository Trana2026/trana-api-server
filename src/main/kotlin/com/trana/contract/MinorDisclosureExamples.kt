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

    const val TEMPLATE_RESPONSE = """
              {
                "version": "v1",
                "title": "미성년자와의 거래입니다",
                "items": [
                  "거래 상대방은 만 19세 미만의 미성년자입니다.",
                  "이 계약은 미성년자 본인 또는 보호자가 취소할 수 있습니다.",
                  "취소되면 이미 지급한 대금을 전부 돌려받지 못할 수 있습니다.\n미성년자는 남아있는 이익만 반환하면 되기 때문입니다.",
                  "서명하시면 위 내용을 확인한 것으로 보아,\n이후 계약을 철회할 수 없습니다.",
                  "확인하신 사실과 시각이 기록됩니다."
                ]
              }
          """
}
