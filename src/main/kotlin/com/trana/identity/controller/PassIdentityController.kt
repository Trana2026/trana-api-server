package com.trana.identity.controller

import com.trana.identity.dto.MOKReqClientInfoResponse
import com.trana.identity.dto.PassReqClientInfoRequest
import com.trana.identity.service.PassReturnService
import com.trana.identity.service.PassSignupService
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/identity/pass")
class PassIdentityController(
    private val passSignupService: PassSignupService,
    private val passReturnService: PassReturnService,
) : PassIdentityApi {
    override fun reqClientInfo(
        @Valid @RequestBody request: PassReqClientInfoRequest,
    ): MOKReqClientInfoResponse = passSignupService.issueReqClientInfo(request.signupSessionId)

    override fun receiveReturn(
        @RequestParam("data") data: String,
    ): ResponseEntity<Void> {
        val redirectUrl = passReturnService.handleReturn(data)
        return ResponseEntity
            .status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, redirectUrl)
            .build()
    }
}
