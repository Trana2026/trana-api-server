package com.trana.trustscore.controller

import com.trana.trustscore.dto.TrustScoreResponse
import com.trana.trustscore.service.TrustScoreService
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/users/me")
@SecurityRequirement(name = "bearerAuth")
class TrustScoreController(
    private val trustScoreService: TrustScoreService,
) : TrustScoreApi {
    override fun getMyTrustScore(
        @AuthenticationPrincipal userId: Long,
    ): TrustScoreResponse = trustScoreService.getMyTrustScore(userId)
}
