package com.trana.contract.controller

import com.trana.contract.service.ContractBlockService
import com.trana.user.dto.BlockUserResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class ContractBlockController(
    private val contractBlockService: ContractBlockService,
) : ContractBlockApi {
    override fun blockCounterparty(
        @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
    ): BlockUserResponse = contractBlockService.blockCounterparty(userId, publicCode)
}
