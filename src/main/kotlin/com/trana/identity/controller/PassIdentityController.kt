package com.trana.identity.controller

import com.trana.identity.dto.MOKReqClientInfoResponse
import com.trana.identity.dto.PassReturnResponse
import com.trana.identity.service.PassReturnService
import com.trana.identity.service.PassSignupService
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/v1/identity/pass")
@Validated
class PassIdentityController(
    private val passSignupService: PassSignupService,
    private val passReturnService: PassReturnService,
) : PassIdentityApi {
    override fun reqClientInfo(
        @RequestParam("signupSessionId") signupSessionId: UUID,
    ): MOKReqClientInfoResponse = passSignupService.issueReqClientInfo(signupSessionId)

    override fun receiveReturn(
        @RequestParam("data") data: String,
    ): ResponseEntity<PassReturnResponse> = ResponseEntity.ok(passReturnService.handleReturn(data))
}
