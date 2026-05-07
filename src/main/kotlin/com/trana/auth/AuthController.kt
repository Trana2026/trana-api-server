package com.trana.auth

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val socialSignInService: SocialSignInService) : AuthApi {
    override fun socialSignIn(request: SocialSignInRequest): SignInResponse = socialSignInService.signIn(request)

    override fun refresh(request: RefreshRequest): SignInResponse = socialSignInService.refresh(request)
}
