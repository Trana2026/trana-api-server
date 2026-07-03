package com.trana.user.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.user.UserInquiryExamples
import com.trana.user.dto.CreateInquiryRequest
import com.trana.user.dto.InquiryDetailResponse
import com.trana.user.dto.InquirySummaryResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@Tag(name = "User Inquiry", description = "1:1 문의 (사용자 → 운영자 단방향)")
interface UserInquiryApi {
    @Operation(
        summary = "1:1 문의 작성",
        description = """
사용자 → 운영자 단방향 문의. DB 저장 + Slack 채널 발송.

처리 흐름:
- DB INSERT (publicCode 12자 nanoid 발급)
- Slack webhook 발송 (실패해도 200 — 운영 로그에만 남고 사용자 응답은 성공)
- 운영자는 Slack 보고 사용자 입력 이메일로 직접 회신 (DB 답변 저장 X)

제약:
- email: 필수 + 형식 검증 (user.email 안 활용 — 성인 KYC 가입자는 user.email null)
- title: 1~100자
- content: 1~2000자
          """,
        requestBody =
            SwaggerRequestBody(
                content = [
                    Content(
                        schema = Schema(implementation = CreateInquiryRequest::class),
                        examples = [ExampleObject(name = "default", value = UserInquiryExamples.CREATE_REQUEST)],
                    ),
                ],
            ),
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "작성 성공",
                content = [
                    Content(
                        schema = Schema(implementation = InquirySummaryResponse::class),
                        examples = [ExampleObject(name = "default", value = UserInquiryExamples.CREATE_RESPONSE)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "validation 실패 (email 형식 / title 길이 / content 길이)",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "validation", value = UserInquiryExamples.VALIDATION_FAILED)],
                    ),
                ],
            ),
            ApiResponse(responseCode = "401", description = "인증 누락"),
        ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/inquiries")
    fun createInquiry(
        @Parameter(hidden = true) userId: Long,
        @Valid @RequestBody request: CreateInquiryRequest,
    ): InquirySummaryResponse

    @Operation(
        summary = "1:1 문의 목록 조회 (본인)",
        description = "본인이 작성한 문의 목록 (최신순). 상세 클릭 시 publicCode 로 별도 조회.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [
                    Content(
                        schema = Schema(implementation = InquirySummaryResponse::class),
                        examples = [
                            ExampleObject(name = "list", value = UserInquiryExamples.LIST_RESPONSE),
                            ExampleObject(name = "empty", value = UserInquiryExamples.LIST_EMPTY),
                        ],
                    ),
                ],
            ),
            ApiResponse(responseCode = "401", description = "인증 누락"),
        ],
    )
    @GetMapping("/inquiries")
    fun listMyInquiries(
        @Parameter(hidden = true) userId: Long,
    ): List<InquirySummaryResponse>

    @Operation(
        summary = "1:1 문의 상세 조회 (본인)",
        description = "본인이 작성한 문의 상세 (모달용). 다른 user 의 publicCode 추측 시 404 (정보 누출 방어).",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [
                    Content(
                        schema = Schema(implementation = InquiryDetailResponse::class),
                        examples = [ExampleObject(name = "default", value = UserInquiryExamples.DETAIL_RESPONSE)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "문의 없음 (본인 row 가 아니거나 publicCode 미존재)",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "notFound", value = UserInquiryExamples.NOT_FOUND)],
                    ),
                ],
            ),
            ApiResponse(responseCode = "401", description = "인증 누락"),
        ],
    )
    @GetMapping("/inquiries/{publicCode}")
    fun getMyInquiry(
        @Parameter(hidden = true) userId: Long,
        @PathVariable publicCode: String,
    ): InquiryDetailResponse
}
