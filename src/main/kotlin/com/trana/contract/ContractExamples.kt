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
                  "status": "IN_PROGRESS",
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
                    "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
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
                      "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                      "sortOrder": 0,
                      "uploadedAt": "2026-05-20T10:05:00Z"
                    },
                    {
                      "id": 103,
                      "s3Key": "contracts/Vh7sK2x9Pq3R/attachments/def-uuid",
                      "originalFilename": "screenshot-02.jpg",
                      "contentType": "image/jpeg",
                      "sizeBytes": 612345,
                      "sha256": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
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

    const val AI_EXTRACT_PENDING = """
                  {
                    "extractionId": 9001,
                    "status": "PENDING",
                    "model": "gpt-4o-mini",
                    "promptVersion": "v1",
                    "prefill": null,
                    "latencyMs": null,
                    "usage": null,
                    "errorMessage": null,
                    "extractedAt": "2026-05-20T10:07:00Z"
                  }
              """

    const val AI_EXTRACT_SUCCESS = """
                  {
                    "extractionId": 9001,
                    "status": "SUCCESS",
                    "model": "gpt-4o-mini",
                    "promptVersion": "v1",
                    "prefill": {
                      "product_name": "에어팟 프로 2세대",
                      "price": 180000,
                      "condition_summary": "사용감 적음",
                      "condition_details": "1년 사용, 케이스 미세 흠집 외 기능 정상"
                    },
                    "latencyMs": 7172,
                    "usage": {
                      "prompt_tokens": 74109,
                      "completion_tokens": 142,
                      "total_tokens": 74251
                    },
                    "errorMessage": null,
                    "extractedAt": "2026-05-20T10:07:00Z"
                  }
              """

    const val AI_EXTRACT_FAILED = """
                  {
                    "extractionId": 9001,
                    "status": "FAILED",
                    "model": "gpt-4o-mini",
                    "promptVersion": "v1",
                    "prefill": null,
                    "latencyMs": null,
                    "usage": null,
                    "errorMessage": "Read timed out: OpenAI 호출 응답 대기 초과",
                    "extractedAt": "2026-05-20T10:07:00Z"
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

    const val NOT_ACCESSIBLE = """
                {
                  "type": "about:blank",
                  "title": "CONTRACT_403_NOT_ACCESSIBLE",
                  "status": 403,
                  "detail": "이 계약에 접근할 권한이 없습니다 (publicCode=Vh7sK2x9Pq3R, userId=99)",
                  "code": "CONTRACT_403_NOT_ACCESSIBLE",
                  "timestamp": "2026-05-29T10:00:00Z"
                }
            """

    const val NOT_DRAFT = """
              {
                "type": "about:blank",
                "title": "CONTRACT_409_NOT_DRAFT",
                "status": 409,
                "detail": "DRAFT 상태에서만 수정/삭제할 수 있습니다 (publicCode=Vh7sK2x9Pq3R, status=SHARED)",
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

    // ───── 상태 전이 ─────

    const val READY_RESPONSE = """
                  {
                    "publicCode": "Vh7sK2x9Pq3R",
                    "status": "READY",
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
                    "contentHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                    "createdAt": "2026-05-20T10:00:00Z",
                    "updatedAt": "2026-05-20T10:15:00Z"
                  }
              """

    const val STATUS_LOGS_RESPONSE = """
                  [
                    {
                      "id": 1,
                      "fromStatus": null,
                      "toStatus": "DRAFT",
                      "actorUserId": 42,
                      "reason": null,
                      "changedAt": "2026-05-20T10:00:00Z"
                    },
                    {
                      "id": 2,
                      "fromStatus": "DRAFT",
                      "toStatus": "READY",
                      "actorUserId": 42,
                      "reason": null,
                      "changedAt": "2026-05-20T10:15:00Z"
                    }
                  ]
              """

    const val NOT_READY_ELIGIBLE = """
                {
                  "type": "about:blank",
                  "title": "CONTRACT_400_NOT_READY",
                  "status": 400,
                  "detail": "READY 전이 불가 — 누락 필드: title, price (publicCode=Vh7sK2x9Pq3R)",
                  "code": "CONTRACT_400_NOT_READY",
                  "timestamp": "2026-05-20T10:15:00Z"
                }
            """

    const val NOT_IN_READY_STATE = """
                {
                  "type": "about:blank",
                  "title": "CONTRACT_409_NOT_READY",
                  "status": 409,
                  "detail": "현재 READY 상태가 아닙니다 (publicCode=Vh7sK2x9Pq3R, status=DRAFT)",
                  "code": "CONTRACT_409_NOT_READY",
                  "timestamp": "2026-05-20T10:15:00Z"
                }
            """

    const val SHARE_REQUEST = """
                  {
                    "receiverName": "홍길동",
                    "receiverPhone": "010-1234-5678"
                  }
              """

    const val SHARED_RESPONSE = """
                    {
                      "publicCode": "Vh7sK2x9Pq3R",
                      "status": "SHARED",
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
                      "contentHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                      "createdAt": "2026-05-20T10:00:00Z",
                      "updatedAt": "2026-05-28T10:30:00Z"
                    }
                """

    const val SHARE_VALIDATION_FAILED = """
                  {
                    "type": "about:blank",
                    "title": "Bad Request",
                    "status": 400,
                    "detail": "receiverPhone: 전화번호 형식이 올바르지 않습니다",
                    "code": "VALIDATION_FAILED",
                    "timestamp": "2026-05-28T10:30:00Z"
                  }
              """

    // ───── 수정 요청 (REVISION) ─────

    const val REVISION_REQUEST_BODY = """
                  {
                    "titleReason": "상품명을 더 정확히 작성해주세요",
                    "priceReason": "150,000원으로 조정 부탁드립니다"
                  }
              """

    const val REVISION_REQUESTED_RESPONSE = """
                    {
                      "publicCode": "Vh7sK2x9Pq3R",
                      "status": "REVISION_REQUESTED",
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
                      "contentHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                      "createdAt": "2026-05-20T10:00:00Z",
                      "updatedAt": "2026-05-28T15:30:00Z"
                    }
                """

    const val REVISION_NO_REASON = """
                  {
                    "type": "about:blank",
                    "title": "Bad Request",
                    "status": 400,
                    "detail": "최소 1개 필드의 reason 은 채워야 합니다",
                    "code": "VALIDATION_FAILED",
                    "timestamp": "2026-05-28T15:30:00Z"
                  }
              """

    const val INVITATION_NOT_FOUND = """
                  {
                    "type": "about:blank",
                    "title": "CONTRACT_404_INVITATION",
                    "status": 404,
                    "detail": "초대 토큰을 찾을 수 없습니다 (token=eZjPZrGyV7iBNPNHT5zqA)",
                    "code": "CONTRACT_404_INVITATION",
                    "timestamp": "2026-05-28T15:30:00Z"
                  }
              """

    const val INVITATION_EXPIRED = """
                  {
                    "type": "about:blank",
                    "title": "CONTRACT_410_INVITATION_EXPIRED",
                    "status": 410,
                    "detail": "이미 사용되었거나 만료된 초대 토큰입니다 (token=eZjPZrGyV7iBNPNHT5zqA)",
                    "code": "CONTRACT_410_INVITATION_EXPIRED",
                    "timestamp": "2026-05-28T15:30:00Z"
                  }
              """

    const val NOT_IN_SHARED_STATE = """
                  {
                    "type": "about:blank",
                    "title": "CONTRACT_409_NOT_SHARED",
                    "status": 409,
                    "detail": "현재 SHARED 상태가 아닙니다 (publicCode=Vh7sK2x9Pq3R, status=DRAFT)",
                    "code": "CONTRACT_409_NOT_SHARED",
                    "timestamp": "2026-05-28T15:30:00Z"
                  }
              """

    const val USER_NOT_READY = """
                    {
                      "type": "about:blank",
                      "title": "CONTRACT_403_USER_NOT_READY",
                      "status": 403,
                      "detail": "가입이 완료되지 않은 사용자입니다 (userId=42, 미성년 보호자 검증 미완료)",
                      "code": "CONTRACT_403_USER_NOT_READY",
                      "timestamp": "2026-05-28T15:30:00Z"
                    }
                """

    // ───── PDF ─────

    const val PDF_DOWNLOAD_RESPONSE = """
                  {
                    "downloadUrl": "https://trana-pdf-archive-dev.s3.ap-northeast-2.amazonaws.com/contracts/Vh7sK2x9Pq3R/pdf.pdf?X-Amz-Algorithm=...&X-Amz-Signature=...",
                    "expiresInSeconds": 600,
                    "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
                  }
              """

    const val PDF_NOT_GENERATED = """
                {
                  "type": "about:blank",
                  "title": "CONTRACT_409_PDF_NOT_GENERATED",
                  "status": 409,
                  "detail": "PDF 가 아직 생성되지 않았습니다 (publicCode=Vh7sK2x9Pq3R, status=DRAFT, markReady 가 선행 필요)",
                  "code": "CONTRACT_409_PDF_NOT_GENERATED",
                  "timestamp": "2026-05-20T10:15:00Z"
                }
            """
}
