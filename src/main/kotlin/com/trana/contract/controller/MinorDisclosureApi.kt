package com.trana.contract.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.contract.MinorDisclosureExamples
import com.trana.contract.dto.ConfirmMinorDisclosureRequest
import com.trana.contract.dto.MinorDisclosureConfirmationResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@Tag(
    name = "Contract Minor Disclosure",
    description = "미성년자 계약 상대방(성인) 서명 전 위험 고지 확인 (민법 제16조 제1항 단서)",
)
interface MinorDisclosureApi {
    @Operation(
        summary = "위험 고지 확인 (성인 → 미성년 상대)",
        description = """
  미성년자와 거래하는 상대방(성인)이 서명 전 위험 고지 5개 항목을 확인했음을 기록.

  동작:
  - 상대(counterpart)가 미성년자가 아니면 409 (호출 자체 불필요)
  - 재확인 시 기존 row 삭제 후 새로 저장 — 최신 IP/UA/템플릿 버전 반영
  - 이용약관 제32조 제2항 의무 + 분쟁 시 고지 입증 유일 수단

  효과:
  - 이후 성인의 서명 endpoint 진입 게이트 통과
  - 민법 제16조 제1항 단서 — 상대가 미성년자임을 알고 계약한 성인은 철회권 상실

  파라미터:
  - disclosedAt: 프론트 화면 표시 시각 (audit)
  - templateVersion: 프론트 표시 문구 버전 (미전송 시 서버 최신)
          """,
        requestBody =
            io.swagger.v3.oas.annotations.parameters.RequestBody(
                content = [
                    Content(
                        schema = Schema(implementation = ConfirmMinorDisclosureRequest::class),
                        examples = [
                            ExampleObject(name = "confirm", value = MinorDisclosureExamples.CONFIRM_REQUEST),
                        ],
                    ),
                ],
            ),
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "확인 저장 성공",
                content = [
                    Content(
                        schema = Schema(implementation = MinorDisclosureConfirmationResponse::class),
                        examples = [
                            ExampleObject(name = "confirmed", value = MinorDisclosureExamples.CONFIRM_RESPONSE),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "토큰 없음 / 만료 / 위변조",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "unauthorized", value = MinorDisclosureExamples.UNAUTHORIZED)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "계약 없음",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "not-found", value = MinorDisclosureExamples.CONTRACT_NOT_FOUND),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "상대방이 미성년자가 아님",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(
                                name = "not-applicable",
                                value = MinorDisclosureExamples.NOT_APPLICABLE,
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/{publicCode}/minor-disclosure/confirm")
    fun confirm(
        @Parameter(hidden = true) userId: Long,
        @PathVariable publicCode: String,
        @Valid @RequestBody request: ConfirmMinorDisclosureRequest,
        @Parameter(hidden = true) httpRequest: HttpServletRequest,
    ): MinorDisclosureConfirmationResponse
}
