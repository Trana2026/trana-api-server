package com.trana.identity.controller

import com.trana.identity.dto.MOKReqClientInfoResponse
import com.trana.identity.dto.PassGuardianReqClientInfoRequest
import com.trana.identity.service.PassGuardianSignupService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/identity/guardian/pass")
class PassGuardianIdentityController(
    private val passGuardianSignupService: PassGuardianSignupService,
) : PassGuardianIdentityApi {
    override fun requestGuardianClientInfo(
        @Valid @RequestBody request: PassGuardianReqClientInfoRequest,
    ): MOKReqClientInfoResponse = passGuardianSignupService.issueReqClientInfo(request.token)
}
