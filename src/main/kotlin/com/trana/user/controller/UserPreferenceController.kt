package com.trana.user.controller

import com.trana.user.dto.PushEnabledResponse
import com.trana.user.dto.UpdatePushEnabledRequest
import com.trana.user.service.UserService
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/users/me")
@SecurityRequirement(name = "bearerAuth")
class UserPreferenceController(
    private val userService: UserService,
) : UserPreferenceApi {
    override fun changePushEnabled(
        @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: UpdatePushEnabledRequest,
    ): PushEnabledResponse {
        val user = userService.changePushEnabled(userId, request.enabled)
        return PushEnabledResponse(pushEnabled = user.pushEnabled)
    }
}
