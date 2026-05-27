package com.trana.contract.controller

import com.trana.contract.dto.ApproveContractGuardianConsentRequest
import com.trana.contract.dto.ContractGuardianConsentApprovedResponse
import com.trana.contract.dto.ContractGuardianConsentLinkResponse
import com.trana.contract.entity.Contract
import com.trana.contract.service.ContractGuardianConsentService
import com.trana.guardian.GuardianProperties
import com.trana.guardian.entity.GuardianLink
import io.swagger.v3.oas.annotations.Parameter
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/contracts")
class ContractGuardianConsentController(
    private val service: ContractGuardianConsentService,
    private val guardianProperties: GuardianProperties,
) : ContractGuardianConsentApi {
    override fun request(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
    ): ContractGuardianConsentLinkResponse {
        val link = service.requestConsent(publicCode = publicCode, minorUserId = userId)
        return link.toLinkResponse(guardianProperties.webBaseUrl)
    }

    override fun approve(
        @RequestBody request: ApproveContractGuardianConsentRequest,
    ): ContractGuardianConsentApprovedResponse {
        val contract = service.approveConsent(token = request.token, guardianId = request.guardianId)
        return contract.toApprovedResponse()
    }
}

private fun GuardianLink.toLinkResponse(webBaseUrl: String): ContractGuardianConsentLinkResponse =
    ContractGuardianConsentLinkResponse(
        token = token,
        expiresAt = expiresAt,
        verifyUrl = "$webBaseUrl/contract?token=$token&openExternalBrowser=1",
    )

private fun Contract.toApprovedResponse(): ContractGuardianConsentApprovedResponse =
    ContractGuardianConsentApprovedResponse(
        publicCode = publicCode,
        guardianConsentAt =
            requireNotNull(guardianConsentAt) {
                "guardianConsentAt 은 approveConsent 직후 채워져야 함 (markGuardianConsented 호출됨)"
            },
    )
