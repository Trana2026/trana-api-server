package com.trana.auth

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/auth")
class AuthController(
    private val socialSignInService: SocialSignInService,
) : AuthApi {
    override fun refresh(request: RefreshRequest): SignInResponse = socialSignInService.refresh(request)

    override fun logout(
        @AuthenticationPrincipal userId: Long,
        request: LogoutRequest,
    ) {
        socialSignInService.logout(userId, request.deviceToken)
    }
}
