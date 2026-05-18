package com.trana.user

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/users")
@SecurityRequirement(name = "bearerAuth")
class UserController(
    private val userService: UserService,
) : UserApi {
    override fun getMe(
        @AuthenticationPrincipal userId: Long,
    ): MeResponse = userService.getById(userId).toMeResponse()

    override fun declareMinor(
        @AuthenticationPrincipal userId: Long,
    ): MeResponse = userService.declareMinor(userId).toMeResponse()
}

private fun User.toMeResponse(): MeResponse =
    MeResponse(
        publicCode = publicCode,
        email = email,
        nickname = nickname,
        status = status,
        ageGroup = ageGroup,
        guardianVerifiedAt = guardianVerifiedAt,
    )
