package com.trana.identity

internal object IdentityExamples {
    const val OCR_SUCCESS = """
            {
              "requestId": "20a4b2c9-1f3e-4a7d-9c1b-8e5f2a3b4c5d",
              "idType": "ID_CARD",
              "name": "홍길동",
              "birthDate": "1990-01-01",
              "gender": "MALE"
            }
        """

    const val VERIFY_REQUEST = """
            { "requestId": "20a4b2c9-1f3e-4a7d-9c1b-8e5f2a3b4c5d" }
        """

    const val VERIFY_SUCCESS = """
            { "requestId": "20a4b2c9-1f3e-4a7d-9c1b-8e5f2a3b4c5d", "verified": true }
        """

    const val PHONE_REQUEST = """
            { "requestId": "20a4b2c9-1f3e-4a7d-9c1b-8e5f2a3b4c5d", "phone": "01012345678" }
        """

    const val PHONE_SUCCESS = """
            { "requestId": "20a4b2c9-1f3e-4a7d-9c1b-8e5f2a3b4c5d", "phone": "01012345678" }
        """

    const val SIGNUP_SUCCESS = """
            {
              "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJ0cmFuYSIsInN1YiI6IjEifQ...",
              "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJ0cmFuYSIsInN1YiI6IjEifQ...",
              "publicCode": "Vh7sK2x9Pq3R",
              "requiresGuardian": false
            }
        """

    const val DUPLICATE = """
            {
              "type": "about:blank",
              "title": "IDENTITY_409_DUPLICATE",
              "status": 409,
              "detail": "이미 본인인증된 사용자입니다 (hash=abc12345...)",
              "code": "IDENTITY_409_DUPLICATE",
              "timestamp": "2026-05-19T12:00:00Z"
            }
        """

    const val SIGNUP_EXPIRED = """
            {
              "type": "about:blank",
              "title": "IDENTITY_410_SIGNUP",
              "status": 410,
              "detail": "가입 세션이 만료되었습니다",
              "code": "IDENTITY_410_SIGNUP",
              "timestamp": "2026-05-19T12:00:00Z"
            }
        """

    const val OCR_REJECTED = """
            {
              "type": "about:blank",
              "title": "IDENTITY_422_OCR",
              "status": 422,
              "detail": "신분증 OCR 인식 실패: ...",
              "code": "IDENTITY_422_OCR",
              "timestamp": "2026-05-19T12:00:00Z"
            }
        """

    const val SESSION_EXPIRED = """
            {
              "type": "about:blank",
              "title": "IDENTITY_410_SESSION",
              "status": 410,
              "detail": "Verify 세션이 만료되었습니다 (OCR을 다시 진행해주세요)",
              "code": "IDENTITY_410_SESSION",
              "timestamp": "2026-05-19T12:00:00Z"
            }
        """

    const val VERIFY_REJECTED = """
            {
              "type": "about:blank",
              "status": 422,
              "title": "IDENTITY_422_VERIFY",
              "detail": "신분증 진위 확인에 실패했습니다. 사진을 다시 찍어 진행해주세요.",
              "instance": "/v1/identity/verify-id-card",
              "code": "IDENTITY_422_VERIFY",
              "timestamp": "2026-05-21T10:00:00Z",
              "ncpCode": "0511",
              "ncpMessage": "암호일련번호가 일치하지 않습니다.",
              "hint": "RETRY_PHOTO"
            }
       """

    const val VERIFY_REQUIRED = """
            {
              "type": "about:blank",
              "title": "IDENTITY_409_VERIFY_REQUIRED",
              "status": 409,
              "detail": "신분증 진위확인을 먼저 완료해주세요",
              "code": "IDENTITY_409_VERIFY_REQUIRED",
              "timestamp": "2026-05-19T12:00:00Z"
            }
        """

    const val COMPARE_REJECTED = """
            {
              "type": "about:blank",
              "title": "IDENTITY_422_COMPARE",
              "status": 422,
              "detail": "얼굴 일치 확인 실패 (similarity=0.32)",
              "code": "IDENTITY_422_COMPARE",
              "timestamp": "2026-05-19T12:00:00Z"
            }
        """

    // ───── 보호자 KYC (Phase 6) ─────

    const val GUARDIAN_VERIFY_REQUEST = """
            {
              "requestId": "20a4b2c9-1f3e-4a7d-9c1b-8e5f2a3b4c5d",
              "token": "V1StGXR8_Z5jdHi6B-myT"
            }
        """

    const val GUARDIAN_BIND_SUCCESS = """
            {
              "subjectUserId": 1,
              "guardianId": 1,
              "verified": true
            }
        """

    const val GUARDIAN_NOT_ADULT = """
            {
              "type": "about:blank",
              "title": "GUARDIAN_403_NOT_ADULT",
              "status": 403,
              "detail": "보호자는 성인(만 19세 이상)이어야 합니다 (hash=abc12345...)",
              "code": "GUARDIAN_403_NOT_ADULT",
              "timestamp": "2026-05-19T12:00:00Z"
            }
        """

    const val GUARDIAN_LINK_INVALID = """
            {
              "type": "about:blank",
              "title": "GUARDIAN_410_LINK",
              "status": 410,
              "detail": "보호자 링크 무효: 만료된 토큰 (token=V1StGXR8...)",
              "code": "GUARDIAN_410_LINK",
              "timestamp": "2026-05-19T12:00:00Z"
            }
        """

    const val GUARDIAN_LINK_NOT_FOUND = """
            {
              "type": "about:blank",
              "title": "GUARDIAN_404_LINK",
              "status": 404,
              "detail": "보호자 링크를 찾을 수 없습니다 (token=V1StGXR8...)",
              "code": "GUARDIAN_404_LINK",
              "timestamp": "2026-05-19T12:00:00Z"
            }
        """
}
