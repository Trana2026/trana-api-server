package com.trana.notification.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.common.response.PageResponse
import com.trana.notification.NotificationExamples
import com.trana.notification.dto.NotificationSummaryResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus

private const val MAX_PAGE_SIZE = 50L

@Tag(name = "Notification", description = "앱 안 알림함 (목록/읽음/삭제)")
interface NotificationApi {
    @Operation(
        summary = "알림 목록 조회 (페이징)",
        description = """
마이페이지 "알림함" 화면용 본인 알림 목록.

정렬: createdAt DESC 서버 강제 (클라이언트 sort 파라미터 X).

파라미터:
- page: 0-based 페이지 번호 (default 0)
- size: 페이지 크기 (default 20, min 1, max 50)

응답 (PageResponse wrapper):
- content: 알림 배열 (NotificationSummaryResponse)
- page / size / totalElements / totalPages / hasNext

각 알림 필드:
- id / category (CONTRACT) / title / body
- deepLink: Flutter 라우팅 URL (null 이면 이동 X)
- isRead: 읽음 여부 (readAt != null)
- readAt: 읽음 처리 시각 (미읽음 시 null)
- createdAt: 알림 생성 시각

미읽음/읽음 필터는 클라이언트에서 isRead 로 처리 (별도 endpoint 없음).
          """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [
                    Content(
                        schema = Schema(implementation = PageResponse::class),
                        examples = [
                            ExampleObject(name = "list", value = NotificationExamples.LIST_RESPONSE),
                            ExampleObject(name = "empty", value = NotificationExamples.LIST_EMPTY),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "page/size 검증 실패",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [
                            ExampleObject(name = "validation", value = NotificationExamples.VALIDATION_FAILED),
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
                        examples = [ExampleObject(name = "unauthorized", value = NotificationExamples.UNAUTHORIZED)],
                    ),
                ],
            ),
        ],
    )
    @GetMapping
    fun list(
        @Parameter(hidden = true) userId: Long,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) size: Int,
    ): PageResponse<NotificationSummaryResponse>

    @Operation(
        summary = "알림 읽음 처리",
        description = """
지정된 알림을 읽음 상태로 전이 (idempotent — 이미 읽음이면 no-op).

권한:
- 본인 알림만 (다른 user 의 id 추측 시 404, 정보 누출 방어)

응답: 204 No Content
          """,
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "읽음 처리 성공 (또는 이미 읽음 — 멱등)"),
            ApiResponse(
                responseCode = "404",
                description = "알림 없음 (본인 소유 아니거나 id 미존재)",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "not-found", value = NotificationExamples.NOT_FOUND)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "토큰 없음 / 만료 / 위변조",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "unauthorized", value = NotificationExamples.UNAUTHORIZED)],
                    ),
                ],
            ),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PatchMapping("/{id}/read")
    fun markRead(
        @Parameter(hidden = true) userId: Long,
        @PathVariable id: Long,
    )

    @Operation(
        summary = "알림 삭제 (hard delete)",
        description = """
지정된 알림을 완전 삭제. audit 성격 아님 — 복구 불가.

권한:
- 본인 알림만 (다른 user 의 id 추측 시 404)

응답: 204 No Content
          """,
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "삭제 성공"),
            ApiResponse(
                responseCode = "404",
                description = "알림 없음 (본인 소유 아니거나 id 미존재)",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "not-found", value = NotificationExamples.NOT_FOUND)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "토큰 없음 / 만료 / 위변조",
                content = [
                    Content(
                        schema = Schema(implementation = ProblemDetailResponse::class),
                        examples = [ExampleObject(name = "unauthorized", value = NotificationExamples.UNAUTHORIZED)],
                    ),
                ],
            ),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    fun delete(
        @Parameter(hidden = true) userId: Long,
        @PathVariable id: Long,
    )
}
