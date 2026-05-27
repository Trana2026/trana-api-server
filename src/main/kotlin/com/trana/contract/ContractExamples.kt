package com.trana.contract

internal object ContractExamples {
    // ───── 진입 (eligibility / 보호자 동의 링크) ─────

    const val ELIGIBILITY_ADULT = """
              {
                "ageGroup": "ADULT",
                "consentRequired": false,
                "consentType": "NOT_APPLICABLE"
              }
          """

    const val ELIGIBILITY_MINOR_PENDING = """
              {
                "ageGroup": "MINOR",
                "consentRequired": true,
                "consentType": "GUARDIAN_REQUIRED",
                "guardianConsentAt": null
              }
          """

    const val ELIGIBILITY_MINOR_CONSENTED = """
              {
                "ageGroup": "MINOR",
                "consentRequired": false,
                "consentType": "GUARDIAN_REQUIRED",
                "guardianConsentAt": "2026-05-20T10:30:00Z"
              }
          """

    const val GUARDIAN_CONSENT_LINK_CREATED = """
              {
                "token": "V1StGXR8_Z5jdHi6B-myT",
                "expiresAt": "2026-05-23T10:30:00Z",
                "verifyUrl": "https://guardian.trana.kr/contract?token=V1StGXR8_Z5jdHi6B-myT"
              }
          """

    const val GUARDIAN_CONSENT_APPROVE_REQUEST = """
                {
                  "token": "V1StGXR8_Z5jdHi6B-myT",
                  "guardianId": 42
                }
            """

    const val GUARDIAN_CONSENT_APPROVE_RESPONSE = """
                {
                  "publicCode": "Vh7sK2x9Pq3R",
                  "guardianConsentAt": "2026-05-20T11:00:00Z"
                }
            """

    const val GUARDIAN_CONSENT_ALREADY = """
                {
                  "type": "about:blank",
                  "title": "CONTRACT_409_GUARDIAN_ALREADY",
                  "status": 409,
                  "detail": "이미 보호자 동의가 완료된 계약입니다 (publicCode=Vh7sK2x9Pq3R)",
                  "code": "CONTRACT_409_GUARDIAN_ALREADY",
                  "timestamp": "2026-05-20T11:00:00Z"
                }
            """

    const val GUARDIAN_CONSENT_LINK_INVALID = """
                {
                  "type": "about:blank",
                  "title": "GUARDIAN_LINK_INVALID",
                  "status": 410,
                  "detail": "이미 사용되었거나 만료된 보호자 링크 토큰",
                  "code": "GUARDIAN_LINK_INVALID",
                  "timestamp": "2026-05-20T11:00:00Z"
                }
            """

    const val GUARDIAN_CONSENT_INVALID_CONSENT_TYPE = """
                {
                  "type": "about:blank",
                  "title": "CONTRACT_400_CONSENT_TYPE",
                  "status": 400,
                  "detail": "보호자 동의가 불필요한 계약입니다 (consentType=NOT_APPLICABLE)",
                  "code": "CONTRACT_400_CONSENT_TYPE",
                  "timestamp": "2026-05-20T11:00:00Z"
                }
            """

    // ───── DRAFT 생성 / 수정 / 삭제 ─────

    const val DRAFT_CREATE_REQUEST = """
                {
                  "creatorRole": "SELLER",
                  "deliveryType": "DIRECT"
                }
            """

    const val DRAFT_CREATE_RESPONSE = """
                {
                  "publicCode": "Vh7sK2x9Pq3R",
                  "status": "DRAFT",
                  "disputeState": "NONE",
                  "deliveryType": "DIRECT",
                  "consentType": "NOT_APPLICABLE",
                  "title": null,
                  "price": null,
                  "conditionSummary": null,
                  "conditionDetails": null,
                  "warrantyPeriodDays": 3,
                  "location": null,
                  "guardianConsentAt": null,
                  "version": 1,
                  "createdAt": "2026-05-20T10:00:00Z",
                  "updatedAt": "2026-05-20T10:00:00Z"
                }
            """

    const val DRAFT_UPDATE_REQUEST = """
              {
                "title": "에어팟 프로 2세대",
                "price": 180000,
                "conditionSummary": "사용감 적음",
                "conditionDetails": "1년 사용, 케이스 미세 흠집 외 기능 정상",
                "deliveryType": "SHIPPING",
                "location": "서울 강남구"
              }
          """

    // ───── 첨부 (presigned upload → register → list) ─────

    const val ATTACHMENT_PRESIGN_REQUEST = """
                {
                  "contentType": "image/jpeg"
                }
            """

    const val ATTACHMENT_PRESIGN_RESPONSE = """
                {
                  "uploadUrl": "https://trana-archive-dev.s3.ap-northeast-2.amazonaws.com/contracts/Vh7sK2x9Pq3R/attachments/abc-uuid?X-Amz-Algorithm=...&X-Amz-Signature=...",
                  "s3Key": "contracts/Vh7sK2x9Pq3R/attachments/abc-uuid",
                  "expiresAt": "2026-05-20T10:10:00Z"
                }
            """

    // ───── 첨부 / AI 추출 ─────

    const val ATTACHMENT_REGISTER_REQUEST = """
                {
                  "s3Key": "contracts/Vh7sK2x9Pq3R/attachments/abc-uuid",
                  "originalFilename": "screenshot-01.jpg",
                  "contentType": "image/jpeg",
                  "sizeBytes": 524288
                }
            """

    const val ATTACHMENT_REGISTER_RESPONSE = """
                {
                  "id": 101,
                  "s3Key": "contracts/Vh7sK2x9Pq3R/attachments/abc-uuid",
                  "originalFilename": "screenshot-01.jpg",
                  "contentType": "image/jpeg",
                  "sizeBytes": 524288,
                  "sortOrder": 0,
                  "uploadedAt": "2026-05-20T10:05:00Z"
                }
            """

    const val ATTACHMENT_LIST_RESPONSE = """
                [
                  {
                    "id": 101,
                    "s3Key": "contracts/Vh7sK2x9Pq3R/attachments/abc-uuid",
                    "originalFilename": "screenshot-01.jpg",
                    "contentType": "image/jpeg",
                    "sizeBytes": 524288,
                    "sortOrder": 0,
                    "uploadedAt": "2026-05-20T10:05:00Z"
                  },
                  {
                    "id": 103,
                    "s3Key": "contracts/Vh7sK2x9Pq3R/attachments/def-uuid",
                    "originalFilename": "screenshot-02.jpg",
                    "contentType": "image/jpeg",
                    "sizeBytes": 612345,
                    "sortOrder": 1,
                    "uploadedAt": "2026-05-20T10:05:30Z"
                  }
                ]
            """

    const val AI_EXTRACT_REQUEST = """
                {
                  "attachmentIds": [101, 103],
                  "consentedAt": "2026-05-20T10:07:00Z"
                }
            """

    const val AI_EXTRACT_RESPONSE = """
                {
                  "extractionId": 9001,
                  "model": "gpt-4o-mini",
                  "promptVersion": "v1",
                  "extractedAt": "2026-05-20T10:07:15Z",
                  "latencyMs": 1842,
                  "usage": {
                    "prompt_tokens": 1250,
                    "completion_tokens": 84,
                    "total_tokens": 1334
                  },
                  "prefill": {
                    "product_name": "에어팟 프로 2세대",
                    "price": 180000,
                    "condition_summary": "사용감 적음",
                    "condition_details": "1년 사용, 케이스 미세 흠집 외 기능 정상"
                  }
                }
            """

    // ───── 상세 / 목록 ─────

    const val DETAIL_RESPONSE = """
                {
                  "publicCode": "Vh7sK2x9Pq3R",
                  "status": "DRAFT",
                  "disputeState": "NONE",
                  "deliveryType": "DIRECT",
                  "consentType": "NOT_APPLICABLE",
                  "title": "에어팟 프로 2세대",
                  "price": 180000,
                  "conditionSummary": "사용감 적음",
                  "conditionDetails": "1년 사용, 케이스 미세 흠집 외 기능 정상",
                  "warrantyPeriodDays": 3,
                  "location": "서울 강남구",
                  "guardianConsentAt": null,
                  "version": 1,
                  "createdAt": "2026-05-20T10:00:00Z",
                  "updatedAt": "2026-05-20T10:07:00Z"
                }
            """

    const val LIST_RESPONSE = """
                [
                  {
                    "publicCode": "Vh7sK2x9Pq3R",
                    "status": "DRAFT",
                    "title": "에어팟 프로 2세대",
                    "price": 180000,
                    "updatedAt": "2026-05-20T10:07:00Z"
                  }
                ]
            """

    // ───── 에러 (ProblemDetail) ─────

    const val NOT_FOUND = """
              {
                "type": "about:blank",
                "title": "CONTRACT_404",
                "status": 404,
                "detail": "계약을 찾을 수 없습니다 (publicCode=Vh7sK2x9Pq3R)",
                "code": "CONTRACT_404",
                "timestamp": "2026-05-20T10:00:00Z"
              }
          """

    const val NOT_OWNER = """
              {
                "type": "about:blank",
                "title": "CONTRACT_403_OWNER",
                "status": 403,
                "detail": "본인이 작성한 계약만 수정할 수 있습니다 (publicCode=Vh7sK2x9Pq3R, userId=42)",
                "code": "CONTRACT_403_OWNER",
                "timestamp": "2026-05-20T10:00:00Z"
              }
          """

    const val NOT_DRAFT = """
              {
                "type": "about:blank",
                "title": "CONTRACT_409_NOT_DRAFT",
                "status": 409,
                "detail": "DRAFT 상태에서만 수정/삭제할 수 있습니다 (publicCode=Vh7sK2x9Pq3R, status=SIGN_REQUESTED)",
                "code": "CONTRACT_409_NOT_DRAFT",
                "timestamp": "2026-05-20T10:00:00Z"
              }
          """

    const val ALREADY_DELETED = """
              {
                "type": "about:blank",
                "title": "CONTRACT_410_DELETED",
                "status": 410,
                "detail": "이미 삭제된 계약입니다 (publicCode=Vh7sK2x9Pq3R)",
                "code": "CONTRACT_410_DELETED",
                "timestamp": "2026-05-20T10:00:00Z"
              }
          """

    const val MAX_ATTACHMENTS = """
              {
                "type": "about:blank",
                "title": "CONTRACT_409_ATTACHMENTS",
                "status": 409,
                "detail": "사진은 최대 7장까지 업로드할 수 있습니다 (publicCode=Vh7sK2x9Pq3R, current=7)",
                "code": "CONTRACT_409_ATTACHMENTS",
                "timestamp": "2026-05-20T10:00:00Z"
              }
          """

    const val GUARDIAN_CONSENT_REQUIRED = """
              {
                "type": "about:blank",
                "title": "CONTRACT_409_GUARDIAN_REQUIRED",
                "status": 409,
                "detail": "보호자 동의가 완료되지 않은 계약입니다 (publicCode=Vh7sK2x9Pq3R)",
                "code": "CONTRACT_409_GUARDIAN_REQUIRED",
                "timestamp": "2026-05-20T10:00:00Z"
              }
          """

    const val AI_EXTRACTION_FAILED = """
              {
                "type": "about:blank",
                "title": "CONTRACT_502_AI",
                "status": 502,
                "detail": "AI 추출 호출에 실패했습니다",
                "code": "CONTRACT_502_AI",
                "timestamp": "2026-05-20T10:00:00Z"
              }
          """

    const val AI_IMAGE_COUNT_INVALID = """
              {
                "type": "about:blank",
                "title": "CONTRACT_400_AI_IMAGE_COUNT",
                "status": 400,
                "detail": "AI 분석 입력 사진 개수 위반 (requested=5, allowed=1~2)",
                "code": "CONTRACT_400_AI_IMAGE_COUNT",
                "timestamp": "2026-05-20T10:00:00Z"
              }
          """

    const val AI_RESPONSE_INVALID = """
              {
                "type": "about:blank",
                "title": "CONTRACT_502_AI_PARSE",
                "status": 502,
                "detail": "AI 응답을 파싱할 수 없습니다: missing field 'product_name'",
                "code": "CONTRACT_502_AI_PARSE",
                "timestamp": "2026-05-20T10:00:00Z"
              }
          """
}
