package com.trana.user.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.terms.dto.MyConsentResponse
import com.trana.user.UserExamples
import com.trana.user.UserInquiryExamples
import com.trana.user.dto.CreateInquiryRequest
import com.trana.user.dto.InquiryDetailResponse
import com.trana.user.dto.InquirySummaryResponse
import com.trana.user.dto.MeResponse
import com.trana.user.dto.PushEnabledResponse
import com.trana.user.dto.UpdatePushEnabledRequest
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
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@Tag(name = "User", description = "사용자")
interface UserApi {
    @Operation(
        summary = "본인 정보 조회",
        description = """
JWT subject(userId) 기준으로 본인 정보 반환. 마이페이지 화면 + 헤더 표시명에 활용.

응답 필드:
- 시스템: publicCode / status / ageGroup / guardianVerifiedAt
- 본인정보: name / birthDate / gender / phone / email
- 푸시 동의: pushEnabled (PATCH /v1/users/me/push-enabled 로 변경)

채움 정책:
- 성인 (ADULT): KYC SUCCESS 시 name/birthDate/gender/phone 모두 채움. email 은 KYC 흐름 미수집 → null
- 미성년 (MINOR, 보호자 인증 완료): name = 소셜 표시명 (provider 자동). birthDate/gender/phone 은 본인 KYC 없어 null
- 미성년 (MINOR, 보호자 인증 미완): guardianVerifiedAt=null. 마이페이지 진입 차단 또는 보호자 인증 유도 화면

미성년자 가입 완료 폴링은 guardianVerifiedAt 필드로 판정.
      """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [
                    Content(
                        schema = Schema(implementation = MeResponse::class),
                        examples = [
                            ExampleObject(name = "adult", value = UserExamples.ME_ADULT),
                            ExampleObject(name = "minor", value = UserExamples.ME_MINOR),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "토큰 없음 / 만료 / 위변조",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "사용자 없음 (USER_404)",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @GetMapping("/me")
    fun getMe(
        @Parameter(hidden = true) userId: Long,
    ): MeResponse

    @Operation(
        summary = "회원 탈퇴",
        description = """
JWT subject(userId) 기준으로 회원 탈퇴 처리.

동작:
- status=WITHDRAWN + withdrawnAt 설정 (soft delete)
- 연관 데이터 (identity_verifications, user_consents, guardian_links)는 보존 (audit + 법적)
- access 토큰은 자연 만료 (15분) — 클라이언트가 로컬 토큰 폐기 필요

재가입:
- 성인: 같은 신분증으로 KYC 재진입 가능 (이전 SUCCESS verification은 ACTIVE 아닌 user 소유라 차단 안 됨)
- 미성년자: 같은 소셜 계정으로 재로그인 시 신규 user 발급 (이전 social 매핑 삭제 후 신규 생성)
          """,
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "탈퇴 성공"),
            ApiResponse(
                responseCode = "401",
                description = "토큰 없음 / 만료 / 위변조",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "사용자 없음 (USER_404)",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "이미 탈퇴됨 (USER_409_ALREADY_WITHDRAWN)",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/me")
    fun withdraw(
        @Parameter(hidden = true) userId: Long,
    )

    @Operation(
        summary = "본인 약관 동의 내역 조회",
        description = """
가입 시 동의한 약관(contextType=SIGNUP) 내역을 최신순으로 반환. 마이페이지 "약관 동의 내역" 화면에 활용.

응답 필드:
- termsId / type / version / title / agreedAt

채움 정책:
- 성인 (ADULT, KYC 가입): 가입 시 동의한 약관 row (서비스/개인정보/마케팅 등)
- 미성년 (MINOR): 빈 배열 — 본인 동의 X, 보호자가 GUARDIAN_CONSENT 흐름으로 동의. Flutter UI 에서 "보호자가 동의한 약관입니다" 안내 처리

본문 조회: GET /v1/terms/{type} 로 현재 활성 버전 본문 조회. 과거 버전은 termsId 기반 별도 lookup 미제공 — 마이페이지에서는 동의 시점 + 제목/버전만 표시.
      """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [
                    Content(
                        schema = Schema(implementation = MyConsentResponse::class),
                        examples = [
                            ExampleObject(name = "adult", value = UserExamples.MY_CONSENTS_ADULT),
                            ExampleObject(name = "minor", value = UserExamples.MY_CONSENTS_EMPTY),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "토큰 없음 / 만료 / 위변조",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @GetMapping("/me/consents")
    fun getMyConsents(
        @Parameter(hidden = true) userId: Long,
    ): List<MyConsentResponse>

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
    @PostMapping("/me/inquiries")
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
    @GetMapping("/me/inquiries")
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
    @GetMapping("/me/inquiries/{publicCode}")
    fun getMyInquiry(
        @Parameter(hidden = true) userId: Long,
        @PathVariable publicCode: String,
    ): InquiryDetailResponse

    @Operation(
        summary = "푸시 알림 토글",
        description = """
  마이페이지 "알림 설정" 화면에서 푸시 알림 수신 동의 변경. 멱등 — 같은 값 반복 호출 OK.

  동작:
  - User.pushEnabled 갱신
  - NotificationDispatchService 가 발송 직전 이 값 검사 → false 면 FCM 발송 skip + 로그

  운영 보류 (W9+):
  - 카테고리별 토글 (예: 계약 알림 / 마케팅 / 시스템) — 현재는 전체 토글만
          """,
        requestBody =
            io.swagger.v3.oas.annotations.parameters.RequestBody(
                content = [
                    Content(
                        schema = Schema(implementation = UpdatePushEnabledRequest::class),
                        examples = [
                            ExampleObject(name = "off", value = UserExamples.PUSH_TOGGLE_OFF),
                            ExampleObject(name = "on", value = UserExamples.PUSH_TOGGLE_ON),
                        ],
                    ),
                ],
            ),
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "변경 성공",
                content = [
                    Content(
                        schema = Schema(implementation = PushEnabledResponse::class),
                        examples = [ExampleObject(name = "default", value = UserExamples.PUSH_TOGGLE_RESPONSE)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "토큰 없음 / 만료 / 위변조",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "사용자 없음 (USER_404)",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @PatchMapping("/me/push-enabled")
    fun changePushEnabled(
        @Parameter(hidden = true) userId: Long,
        @Valid @RequestBody request: UpdatePushEnabledRequest,
    ): PushEnabledResponse
}
