package com.trana.user.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.user.UserPreferenceExamples
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
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody

@Tag(name = "User Preference", description = "사용자 설정 (푸시 토글 등)")
interface UserPreferenceApi {
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
                            ExampleObject(name = "off", value = UserPreferenceExamples.PUSH_TOGGLE_OFF),
                            ExampleObject(name = "on", value = UserPreferenceExamples.PUSH_TOGGLE_ON),
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
                        examples = [
                            ExampleObject(
                                name = "default",
                                value = UserPreferenceExamples.PUSH_TOGGLE_RESPONSE,
                            ),
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
    @PatchMapping("/push-enabled")
    fun changePushEnabled(
        @Parameter(hidden = true) userId: Long,
        @Valid @RequestBody request: UpdatePushEnabledRequest,
    ): PushEnabledResponse
}
