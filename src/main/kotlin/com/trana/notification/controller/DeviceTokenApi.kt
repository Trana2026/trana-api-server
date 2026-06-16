package com.trana.notification.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.notification.DeviceTokenExamples
import com.trana.notification.dto.RegisterDeviceTokenRequest
import com.trana.notification.dto.UnregisterDeviceTokenRequest
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
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus

@Tag(name = "Device Token", description = "FCM 디바이스 토큰 등록 / 해제")
interface DeviceTokenApi {
    @Operation(
        summary = "FCM 디바이스 토큰 등록",
        description = """
앱 최초 실행 또는 FCM 토큰 갱신 시 호출 (명세서 2.4.2 / 토큰 관리).

동작 (멱등):
- 같은 token_hash 의 기존 row + 같은 user → no-op
- 같은 token_hash 의 기존 row + 다른 user → userId 갱신 (단말 재로그인)
- 없으면 새 INSERT
- token 은 AES-256-GCM 암호화 + SHA-256 hash (UNIQUE 매칭) 분리 저장

multi-device 지원 — 한 user 가 여러 단말 (Android + iOS) 동시 등록 가능.
            """,
        requestBody =
            io.swagger.v3.oas.annotations.parameters.RequestBody(
                content = [
                    Content(
                        schema = Schema(implementation = RegisterDeviceTokenRequest::class),
                        examples = [ExampleObject(name = "register", value = DeviceTokenExamples.REGISTER_REQUEST)],
                    ),
                ],
            ),
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "등록 성공"),
            ApiResponse(
                responseCode = "400",
                description = "token 누락 / platform invalid",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "validation", value = DeviceTokenExamples.VALIDATION_FAILED),
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
                        examples = [ExampleObject(name = "unauthorized", value = DeviceTokenExamples.UNAUTHORIZED)],
                    ),
                ],
            ),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping
    fun register(
        @Parameter(hidden = true) userId: Long,
        @Valid @RequestBody request: RegisterDeviceTokenRequest,
    )

    @Operation(
        summary = "FCM 디바이스 토큰 해제",
        description = """
사용자 로그아웃 / 단말 변경 시 호출 (멱등).

동작:
- userId + token_hash 매칭 row 만 삭제
- 없어도 204 (이미 정리됨 / 처음부터 없음 무관)
- 다른 user 의 동일 토큰은 영향 X
            """,
        requestBody =
            io.swagger.v3.oas.annotations.parameters.RequestBody(
                content = [
                    Content(
                        schema = Schema(implementation = UnregisterDeviceTokenRequest::class),
                        examples = [ExampleObject(name = "unregister", value = DeviceTokenExamples.UNREGISTER_REQUEST)],
                    ),
                ],
            ),
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "해제 성공 (또는 처음부터 없음 — 멱등)"),
            ApiResponse(
                responseCode = "400",
                description = "token 누락",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "validation", value = DeviceTokenExamples.VALIDATION_FAILED),
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
                        examples = [ExampleObject(name = "unauthorized", value = DeviceTokenExamples.UNAUTHORIZED)],
                    ),
                ],
            ),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping
    fun unregister(
        @Parameter(hidden = true) userId: Long,
        @Valid @RequestBody request: UnregisterDeviceTokenRequest,
    )
}
