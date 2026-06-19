package com.trana.user.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.terms.dto.MyConsentResponse
import com.trana.user.UserConsentExamples
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping

@Tag(name = "User Consent", description = "본인 약관 동의 내역")
interface UserConsentApi {
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
                            ExampleObject(name = "adult", value = UserConsentExamples.MY_CONSENTS_ADULT),
                            ExampleObject(name = "minor", value = UserConsentExamples.MY_CONSENTS_EMPTY),
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
    @GetMapping("/consents")
    fun getMyConsents(
        @Parameter(hidden = true) userId: Long,
    ): List<MyConsentResponse>
}
