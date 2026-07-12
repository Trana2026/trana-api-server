package com.trana.notification.controller

import com.trana.notification.dto.DeviceTokenSummaryResponse
import com.trana.notification.dto.PingDeviceTokenRequest
import com.trana.notification.dto.RegisterDeviceTokenRequest
import com.trana.notification.dto.RegisterDeviceTokenResponse
import com.trana.notification.dto.UnregisterDeviceTokenRequest
import com.trana.notification.entity.DeviceToken
import com.trana.notification.service.DeviceTokenService
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
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
    ): RegisterDeviceTokenResponse {
        val deviceToken =
            deviceTokenService.register(
                userId = userId,
                token = request.token,
                platform = request.platform,
                deviceModel = request.deviceModel,
                osVersion = request.osVersion,
                appVersion = request.appVersion,
            )
        return RegisterDeviceTokenResponse(id = deviceToken.id!!)
    }

    override fun unregister(
        @AuthenticationPrincipal userId: Long,
        request: UnregisterDeviceTokenRequest,
    ) {
        deviceTokenService.unregister(userId, request.token)
    }

    override fun listMyDevices(
        @AuthenticationPrincipal userId: Long,
    ): List<DeviceTokenSummaryResponse> = deviceTokenService.listMine(userId).map { it.toSummary() }

    override fun forceDelete(
        @AuthenticationPrincipal userId: Long,
        @PathVariable id: Long,
    ) {
        deviceTokenService.forceDelete(userId, id)
    }

    override fun ping(
        @AuthenticationPrincipal userId: Long,
        request: PingDeviceTokenRequest,
    ) {
        deviceTokenService.ping(userId, request.token)
    }
}

private fun DeviceToken.toSummary(): DeviceTokenSummaryResponse =
    DeviceTokenSummaryResponse(
        id = id!!,
        platform = platform,
        deviceModel = deviceModel,
        osVersion = osVersion,
        appVersion = appVersion,
        createdAt = createdAt!!,
        lastUsedAt = lastUsedAt,
    )
