package com.trana.notification.dto

import com.trana.notification.entity.Notification
import com.trana.notification.entity.NotificationCategory
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "알림 리스트 row / 상세 응답")
data class NotificationSummaryResponse(
    @Schema(description = "알림 식별자 — PATCH read / DELETE 시 사용", example = "42")
    val id: Long,
    @Schema(description = "알림 카테고리", example = "CONTRACT")
    val category: NotificationCategory,
    @Schema(description = "알림 제목", example = "새 계약서 도착")
    val title: String,
    @Schema(description = "알림 본문", example = "이테스트B님이 서명을 요청했어요")
    val body: String,
    @Schema(
        description = "Flutter 앱 라우팅 URL. 리스트 탭 시 이동. null 이면 이동 X",
        example = "trana://contracts/CT-XXXX-01",
        nullable = true,
    )
    val deepLink: String?,
    @Schema(description = "읽음 여부", example = "false")
    val isRead: Boolean,
    @Schema(
        description = "읽음 처리 시각 (UTC). 미읽음 시 null",
        nullable = true,
    )
    val readAt: Instant?,
    @Schema(description = "알림 생성 시각 (UTC)")
    val createdAt: Instant,
)

fun Notification.toResponse(): NotificationSummaryResponse =
    NotificationSummaryResponse(
        id = id!!,
        category = category,
        title = title,
        body = body,
        deepLink = deepLink,
        isRead = isRead,
        readAt = readAt,
        createdAt = createdAt!!,
    )
