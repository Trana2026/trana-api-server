package com.trana.user.controller

import com.trana.terms.dto.MyConsentResponse
import com.trana.terms.service.ConsentService
import com.trana.user.dto.MeResponse
import com.trana.user.entity.User
import com.trana.user.service.UserService
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/users")
@SecurityRequirement(name = "bearerAuth")
class UserController(
    private val userService: UserService,
    private val consentService: ConsentService,
) : UserApi {
    override fun getMe(
        @AuthenticationPrincipal userId: Long,
    ): MeResponse = userService.getById(userId).toMeResponse()

    override fun withdraw(
        @AuthenticationPrincipal userId: Long,
    ) {
        userService.withdraw(userId)
    }

    override fun getMyConsents(
        @AuthenticationPrincipal userId: Long,
    ): List<MyConsentResponse> = consentService.findMyConsents(userId)
}

private fun User.toMeResponse(): MeResponse =
    MeResponse(
        publicCode = publicCode,
        email = email,
        status = status,
        ageGroup = ageGroup,
        guardianVerifiedAt = guardianVerifiedAt,
        name = name,
        birthDate = birthDate,
        gender = gender,
        phone = phone,
        pushEnabled = pushEnabled,
    )
