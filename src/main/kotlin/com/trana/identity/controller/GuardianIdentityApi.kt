package com.trana.identity.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.identity.IdentityExamples
import com.trana.identity.dto.GuardianBindResponse
import com.trana.identity.dto.GuardianVerifyIdCardRequest
import com.trana.identity.dto.RecognizeIdCardResponse
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
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.multipart.MultipartFile
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import org.springframework.web.bind.annotation.RequestBody as SpringRequestBody

@Tag(name = "Guardian Identity", description = "보호자 본인 KYC (미성년자 가입 완성용)")
interface GuardianIdentityApi {
    @Operation(
        operationId = "guardianKycStep1RecognizeIdCard",
        summary = "보호자 신분증 OCR (Step 1)",
        description = """
trana-web-guardian에서 호출. 미성년자가 발급한 token 기반 진입.

흐름:
- 사전 조건: 미성년자가 POST /v1/guardian/links로 token 발급 + 보호자에게 공유
- token 검증 + 미성년자 user 상태 검증 (MINOR + 미인증)
- NCP OCR → 보호자 신분증 인식
- 만 19세 미만(보호자 후보가 미성년자)이면 403 NOT_ADULT
- 이미 본인인증된 사람과 동일 신분증이면 409 DUPLICATE
          """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "OCR 성공",
                content = [
                    Content(
                        schema = Schema(implementation = RecognizeIdCardResponse::class),
                        examples = [ExampleObject(name = "success", value = IdentityExamples.OCR_SUCCESS)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "보호자 후보 미성년 / 미성년자 user 아님",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "notAdult", value = IdentityExamples.GUARDIAN_NOT_ADULT),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "token 없음",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "linkNotFound", value = IdentityExamples.GUARDIAN_LINK_NOT_FOUND),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "중복 신분증",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "duplicate", value = IdentityExamples.DUPLICATE)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "410",
                description = "token 만료/사용됨",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "linkInvalid", value = IdentityExamples.GUARDIAN_LINK_INVALID),
                        ],
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
        @Parameter(description = "guardian_links 토큰", required = true)
        @RequestParam("token") token: String,
        @Parameter(description = "보호자 신분증 사진 (image/jpeg, image/png)", required = true)
        @RequestPart("file") file: MultipartFile,
    ): RecognizeIdCardResponse

    @Operation(
        operationId = "guardianKycStep2VerifyIdCard",
        summary = "보호자 신분증 진위확인 (Step 2)",
        description = """
NCP Verify API 호출 — 정부 record 대조.

- 사전 조건: Step 1 OCR 완료
- token + requestId 둘 다 검증 (verification record가 해당 token 흐름인지)
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
                description = "세션 만료 (10분 초과)",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "sessionExpired", value = IdentityExamples.SESSION_EXPIRED),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "422",
                description = "진위확인 실패",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "verifyRejected", value = IdentityExamples.VERIFY_REJECTED),
                        ],
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
                    schema = Schema(implementation = GuardianVerifyIdCardRequest::class),
                    examples = [
                        ExampleObject(name = "default", value = IdentityExamples.GUARDIAN_VERIFY_REQUEST),
                    ],
                ),
            ],
        )
        @SpringRequestBody
        @Valid
        request: GuardianVerifyIdCardRequest,
    ): VerifyIdCardResponse

    @Operation(
        operationId = "kycGuardianPreviewIdCard",
        summary = "보호자 신분증 OCR 이미지 프리뷰",
        description = """
  보호자가 OCR 완료한 신분증 사진을 다시 확인하는 step (Verify 호출 전).

  흐름:
  - 사전 조건: OCR 완료 (requestId + token 보유)
  - 응답: 신분증 사진 byte stream (image/jpeg 또는 image/png)
  - 마스킹 없음 — 개발 단계, 추후 도입 예정

  세션 만료(10분 초과) 시 410. token 불일치 시 410.
      """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "신분증 사진 byte stream",
            ),
            ApiResponse(
                responseCode = "404",
                description = "세션 없음",
                content = [
                    Content(schema = Schema(implementation = ProblemDetailResponse::class)),
                ],
            ),
            ApiResponse(
                responseCode = "410",
                description = "세션 만료 또는 token 불일치",
                content = [
                    Content(schema = Schema(implementation = ProblemDetailResponse::class)),
                ],
            ),
        ],
    )
    @GetMapping("/id-card/image", produces = [MediaType.IMAGE_PNG_VALUE])
    fun previewIdCard(
        @Parameter(description = "OCR step에서 받은 requestId", required = true)
        @RequestParam("requestId") requestId: String,
        @Parameter(description = "보호자 매칭 토큰 (jnanoid 21자)", required = true)
        @RequestParam("token") token: String,
    ): ResponseEntity<ByteArrayResource>

    @Operation(
        operationId = "guardianKycStep3CompareFaces",
        summary = "보호자 얼굴 비교 + 가입 완성 (Step 3)",
        description = """
보호자 셀카 multipart 업로드 → NCP Compare → guardians upsert + 미성년자 user 인증 완료.

흐름:
- 사전 조건: Step 2 Verify 통과 (보호자 phone은 수집 안 함)
- SUCCESS:
- guardians 테이블에 보호자 마스터 upsert (identifier_hash 기준)
- identity_verifications SUCCESS (guardian_id 채움)
- guardian_links.used_at 채움 (재사용 차단)
- users.guardian_verified_at 채움 (미성년자 가입 완료)
- S3 신분증 사진 즉시 삭제
- FAIL (similarity 미달): 422 + audit 보존
          """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "보호자 인증 완료 (미성년자 가입 확정)",
                content = [
                    Content(
                        schema = Schema(implementation = GuardianBindResponse::class),
                        examples = [
                            ExampleObject(name = "success", value = IdentityExamples.GUARDIAN_BIND_SUCCESS),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "422",
                description = "얼굴 일치 실패",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "compareRejected", value = IdentityExamples.COMPARE_REJECTED),
                        ],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/face-compare", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun compareFaces(
        @Parameter(description = "OCR 단계 requestId", required = true)
        @RequestParam("requestId") requestId: String,
        @Parameter(description = "guardian_links 토큰", required = true)
        @RequestParam("token") token: String,
        @Parameter(description = "보호자 셀카 사진", required = true)
        @RequestPart("file") file: MultipartFile,
    ): GuardianBindResponse
}
