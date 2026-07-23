package com.trana.user.controller

import com.trana.contract.service.ContractConsentQueryService
import com.trana.terms.dto.MyConsentResponse
import com.trana.terms.service.ConsentService
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/users/me")
@SecurityRequirement(name = "bearerAuth")
class UserConsentController(
    private val consentService: ConsentService,
    private val contractConsentQueryService: ContractConsentQueryService,
) : UserConsentApi {
    /**
     * 마이페이지 '동의한 약관 목록' — 가입 약관(user_consents) + 계약 도메인 AI 국외이전 동의(contract_consents) 병합.
     * AI 동의는 term 당 최신 1건만 (계약별 나열 X — plan 3-2 A').
     */
    override fun getMyConsents(
        @AuthenticationPrincipal userId: Long,
    ): List<MyConsentResponse> =
        consentService.findMyConsents(userId) + contractConsentQueryService.findUserAiConsents(userId)
}
