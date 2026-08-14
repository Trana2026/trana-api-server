package com.trana.user.controller

import com.trana.user.dto.BlockedUserView
import com.trana.user.service.UserBlockService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class UserBlockController(
    private val userBlockService: UserBlockService,
) : UserBlockApi {
    override fun listBlocked(
        @AuthenticationPrincipal userId: Long,
    ): List<BlockedUserView> = userBlockService.listBlocked(userId)

    override fun unblock(
        @AuthenticationPrincipal userId: Long,
        @PathVariable shareCode: String,
    ) = userBlockService.unblock(userId, shareCode)
}
