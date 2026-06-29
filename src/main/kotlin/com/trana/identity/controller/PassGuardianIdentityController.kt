package com.trana.identity.controller

import com.trana.identity.dto.MOKReqClientInfoResponse
import com.trana.identity.service.PassGuardianSignupService
import jakarta.validation.constraints.NotBlank
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/identity/guardian/pass")
@Validated
class PassGuardianIdentityController(
    private val passGuardianSignupService: PassGuardianSignupService,
) : PassGuardianIdentityApi {
    override fun requestGuardianClientInfo(
        @RequestParam("token") @NotBlank token: String,
    ): MOKReqClientInfoResponse = passGuardianSignupService.issueReqClientInfo(token)
}
