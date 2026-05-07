package com.trana.user

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController(private val userService: UserService) : UserApi {
    override fun getMe(@AuthenticationPrincipal userId: Long): MeResponse {
        val user = userService.getById(userId)
        return MeResponse(
            publicCode = user.publicCode,
            email = user.email,
            nickname = user.nickname,
            status = user.status,
        )
    }
}
