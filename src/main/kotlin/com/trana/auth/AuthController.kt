package com.trana.auth

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 인증 endpoint.
 *
 * 컨트롤러는 얇게 — 검증/매핑/응답만, 비즈니스 로직은 Service에.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val socialSignInService: SocialSignInService) {
    /**
     * 소셜 로그인 (가입 + 로그인 통합).
     *
     * 신규 사용자면 자동 가입, 기존이면 토큰만 재발급.
     *
     * Body: { "provider": "KAKAO", "accessToken": "..." }
     * Response: { "accessToken": "...", "refreshToken": "...", "publicCode": "..." }
     */
    @PostMapping("/social/sign-in")
    fun socialSignIn(@RequestBody request: SocialSignInRequest): SignInResponse = socialSignInService.signIn(request)
}
