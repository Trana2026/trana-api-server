package com.trana.auth

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/auth")
class AuthController(
    private val authService: AuthService,
) : AuthApi {
    override fun refresh(request: RefreshRequest): SignInResponse = authService.refresh(request)

    override fun logout(
        @AuthenticationPrincipal userId: Long,
        request: LogoutRequest,
    ) {
        authService.logout(userId, request.deviceToken)
    }
}
