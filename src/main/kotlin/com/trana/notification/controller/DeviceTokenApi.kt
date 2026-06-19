package com.trana.notification.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.notification.DeviceTokenExamples
import com.trana.notification.dto.DeviceTokenSummaryResponse
import com.trana.notification.dto.PingDeviceTokenRequest
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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
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

    @Operation(
        summary = "내 기기 목록 조회",
        description = """
마이페이지 "기기 관리" 화면용 본인 단말 목록 (등록순 desc).

응답 필드:
- id / platform / createdAt / lastUsedAt

lastUsedAt: Flutter 가 앱 foreground 진입 시 ping endpoint 호출 → 갱신. 등록 직후 신규 단말은 null.

"현재 단말" 식별은 응답에 노출 X — Flutter 가 자기 id 기억해서 비교 (강제 해제 confirm 다이얼로그로 실수 방어).
          """,
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공"),
            ApiResponse(
                responseCode = "401",
                description = "토큰 없음 / 만료 / 위변조",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @GetMapping
    fun listMyDevices(
        @Parameter(hidden = true) userId: Long,
    ): List<DeviceTokenSummaryResponse>

    @Operation(
        summary = "기기 강제 해제 (특정 단말)",
        description = """
마이페이지에서 다른 단말 (또는 자기 단말) 강제 해제. FCM token 만 정리 — JWT blacklist 는 X (stateless 정책, access 15분 자연 만료).

권한:
- 본인 row 만 (다른 user 의 id 추측 시 404, 정보 누출 방어)
          """,
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "해제 성공"),
            ApiResponse(
                responseCode = "404",
                description = "기기 없음 (본인 row 가 아니거나 id 미존재)",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "토큰 없음 / 만료 / 위변조",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    fun forceDelete(
        @Parameter(hidden = true) userId: Long,
        @PathVariable id: Long,
    )

    @Operation(
        summary = "기기 활성 ping",
        description = """
Flutter 가 앱 foreground 진입 시 호출 — 본인 단말의 lastUsedAt 갱신.

동작 (멱등):
- 본인 token 매칭 → markUsed
- 매칭 실패 (등록 안 된 token / 다른 user token 추측) → silent ignore (정상 200)
          """,
        requestBody =
            io.swagger.v3.oas.annotations.parameters.RequestBody(
                content = [
                    Content(
                        schema = Schema(implementation = PingDeviceTokenRequest::class),
                    ),
                ],
            ),
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "ping 성공 (매칭 실패 시에도 204 — 멱등)"),
            ApiResponse(
                responseCode = "400",
                description = "token 누락",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "토큰 없음 / 만료 / 위변조",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/ping")
    fun ping(
        @Parameter(hidden = true) userId: Long,
        @Valid @RequestBody request: PingDeviceTokenRequest,
    )
}
