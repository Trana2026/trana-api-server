package com.trana.notification.controller

import com.trana.common.response.PageResponse
import com.trana.notification.dto.NotificationSummaryResponse
import com.trana.notification.service.NotificationService
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/notifications")
@Validated
@SecurityRequirement(name = "bearerAuth")
class NotificationController(
    private val notificationService: NotificationService,
) : NotificationApi {
    override fun list(
        @AuthenticationPrincipal userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<NotificationSummaryResponse> = notificationService.list(userId, page, size)

    override fun markRead(
        @AuthenticationPrincipal userId: Long,
        @PathVariable id: Long,
    ) {
        notificationService.markRead(userId, id)
    }

    override fun delete(
        @AuthenticationPrincipal userId: Long,
        @PathVariable id: Long,
    ) {
        notificationService.delete(userId, id)
    }
}
