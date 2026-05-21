package com.trana.identity.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.identity.IdentityExamples
import com.trana.identity.dto.RecognizeIdCardResponse
import com.trana.identity.dto.RecordPhoneRequest
import com.trana.identity.dto.RecordPhoneResponse
import com.trana.identity.dto.SignUpResponse
import com.trana.identity.dto.VerifyIdCardRequest
import com.trana.identity.dto.VerifyIdCardResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.multipart.MultipartFile
import java.util.UUID
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import org.springframework.web.bind.annotation.RequestBody as SpringRequestBody

@Tag(name = "Identity", description = "성인 본인 KYC")
interface IdentityApi {
    @Operation(
        operationId = "kycStep1RecognizeIdCard",
        summary = "신분증 OCR (Step 1)",
        description = """
  신분증 사진 multipart 업로드 → NCP OCR → 임시 세션 발급 (10분 TTL).

  흐름:
  - 사전 조건: POST /v1/consents 로 signupSessionId 발급 (30분 TTL)
  - 응답의 requestId는 이후 step에 그대로 전달
  - 3종 신분증 지원: 주민등록증 / 운전면허증 / 외국인등록증
  - 파일: image/jpeg 또는 image/png

  차단:
  - identifier_hash 중복 (이미 본인인증된 사용자) → 409
  - signup 세션 만료 → 410
          """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "OCR 인식 성공",
                content = [
                    Content(
                        schema = Schema(implementation = RecognizeIdCardResponse::class),
                        examples = [ExampleObject(name = "success", value = IdentityExamples.OCR_SUCCESS)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "중복 가입",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "duplicate", value = IdentityExamples.DUPLICATE)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "410",
                description = "가입 세션 만료",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "signupExpired", value = IdentityExamples.SIGNUP_EXPIRED)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "422",
                description = "OCR 인식 실패",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "ocrRejected", value = IdentityExamples.OCR_REJECTED)],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/id-card", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun recognizeIdCard(
        @Parameter(description = "약관 동의 단계에서 받은 signupSessionId", required = true)
        @RequestParam("signupSessionId") signupSessionId: UUID,
        @Parameter(description = "신분증 사진 (image/jpeg, image/png)", required = true)
        @RequestPart("file") file: MultipartFile,
    ): RecognizeIdCardResponse

    @Operation(
        operationId = "kycStep2VerifyIdCard",
        summary = "신분증 진위확인 (Step 2)",
        description = """
  NCP Verify API 호출 → 정부 record와 일치 검증.

  흐름:
  - 사전 조건: Step 1 OCR 완료 (requestId 보유)
  - 실패 시 422 + failureStep=VERIFY 로 audit 보존 (재시도 시 OCR부터 다시)
          """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "진위확인 성공",
                content = [
                    Content(
                        schema = Schema(implementation = VerifyIdCardResponse::class),
                        examples = [ExampleObject(name = "success", value = IdentityExamples.VERIFY_SUCCESS)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "410",
                description = "세션 만료 (10분 초과 — OCR 재진행 필요)",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "sessionExpired", value = IdentityExamples.SESSION_EXPIRED)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "422",
                description = "진위확인 실패",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "verifyRejected", value = IdentityExamples.VERIFY_REJECTED)],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/verify-id-card")
    fun verifyIdCard(
        @SwaggerRequestBody(
            required = true,
            content = [
                Content(
                    schema = Schema(implementation = VerifyIdCardRequest::class),
                    examples = [ExampleObject(name = "default", value = IdentityExamples.VERIFY_REQUEST)],
                ),
            ],
        )
        @SpringRequestBody
        @Valid
        request: VerifyIdCardRequest,
    ): VerifyIdCardResponse

    @Operation(
        operationId = "kycStep3RecordPhone",
        summary = "휴대폰 번호 기록 (Step 3)",
        description = """
  Verify 통과 후 휴대폰 번호 저장.

  - 형식: 010 + 8자리 (하이픈 무관, 정규화하여 11자리 숫자만 저장)
  - 실제 SMS 본인인증은 W7+ 도입 예정 (현재는 입력값 저장만)
          """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "기록 성공",
                content = [
                    Content(
                        schema = Schema(implementation = RecordPhoneResponse::class),
                        examples = [ExampleObject(name = "success", value = IdentityExamples.PHONE_SUCCESS)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "Verify 미완료",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "verifyRequired", value = IdentityExamples.VERIFY_REQUIRED)],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/phone")
    fun recordPhone(
        @SwaggerRequestBody(
            required = true,
            content = [
                Content(
                    schema = Schema(implementation = RecordPhoneRequest::class),
                    examples = [ExampleObject(name = "default", value = IdentityExamples.PHONE_REQUEST)],
                ),
            ],
        )
        @SpringRequestBody
        @Valid
        request: RecordPhoneRequest,
    ): RecordPhoneResponse

    @Operation(
        operationId = "kycStep4CompareFaces",
        summary = "얼굴 비교 + 가입 완료 (Step 4)",
        description = """
  셀카 사진 multipart 업로드 → NCP Face Compare → user 생성 + JWT 발급.

  흐름:
  - 사전 조건: Step 3까지 완료 (verifyPassed=true + phone 저장)
  - SUCCESS: user 생성 (ageGroup=ADULT) + JWT 발급 + S3 신분증 사진 삭제 + 약관 백필
  - FAIL (similarity 임계값 미달): 422 + failureStep=COMPARE 로 audit

  응답 토큰:
  - accessToken: 15분
  - refreshToken: 30일
  - requiresGuardian: 성인은 항상 false
          """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "가입 완료",
                content = [
                    Content(
                        schema = Schema(implementation = SignUpResponse::class),
                        examples = [ExampleObject(name = "success", value = IdentityExamples.SIGNUP_SUCCESS)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "422",
                description = "얼굴 일치 실패",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "compareRejected", value = IdentityExamples.COMPARE_REJECTED)],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/face-compare", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun compareFaces(
        @Parameter(description = "OCR 단계에서 받은 requestId", required = true)
        @RequestParam("requestId") requestId: String,
        @Parameter(description = "셀카 사진 (image/jpeg, image/png)", required = true)
        @RequestPart("file") file: MultipartFile,
    ): SignUpResponse
}
