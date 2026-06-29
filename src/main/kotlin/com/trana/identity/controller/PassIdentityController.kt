package com.trana.identity.controller

import com.trana.identity.dto.MOKReqClientInfoResponse
import com.trana.identity.dto.PassReqClientInfoRequest
import com.trana.identity.service.PassSignupService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/identity/pass")
class PassIdentityController(
    private val passSignupService: PassSignupService,
) : PassIdentityApi {
    override fun reqClientInfo(
        @Valid @RequestBody request: PassReqClientInfoRequest,
    ): MOKReqClientInfoResponse = passSignupService.issueReqClientInfo(request.signupSessionId)
}
