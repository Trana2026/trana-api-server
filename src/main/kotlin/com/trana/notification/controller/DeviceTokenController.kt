package com.trana.notification.controller

import com.trana.notification.dto.RegisterDeviceTokenRequest
import com.trana.notification.dto.UnregisterDeviceTokenRequest
import com.trana.notification.service.DeviceTokenService
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/users/me/device-tokens")
@SecurityRequirement(name = "bearerAuth")
class DeviceTokenController(
    private val deviceTokenService: DeviceTokenService,
) : DeviceTokenApi {
    override fun register(
        @AuthenticationPrincipal userId: Long,
        request: RegisterDeviceTokenRequest,
    ) {
        deviceTokenService.register(userId, request.token, request.platform)
    }

    override fun unregister(
        @AuthenticationPrincipal userId: Long,
        request: UnregisterDeviceTokenRequest,
    ) {
        deviceTokenService.unregister(userId, request.token)
    }
}
