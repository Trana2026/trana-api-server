package com.trana.contract.controller

import com.trana.contract.dto.ContractListItem
import com.trana.contract.dto.ContractResponse
import com.trana.contract.dto.CreateContractDraftRequest
import com.trana.contract.dto.UpdateContractDraftRequest
import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractStatus
import com.trana.contract.service.ContractDraftService
import io.swagger.v3.oas.annotations.Parameter
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/contracts")
class ContractDraftController(
    private val service: ContractDraftService,
) : ContractDraftApi {
    override fun createDraft(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @RequestBody request: CreateContractDraftRequest,
    ): ContractResponse {
        val contract =
            service.createDraft(
                creatorUserId = userId,
                deliveryType = request.deliveryType,
                creatorRole = request.creatorRole,
            )
        return contract.toResponse()
    }

    override fun getDetail(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
    ): ContractResponse = service.getDraft(publicCode, userId).toResponse()

    override fun updateDraft(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
        @RequestBody request: UpdateContractDraftRequest,
    ): ContractResponse =
        service
            .updateDraft(
                publicCode = publicCode,
                userId = userId,
                title = request.title,
                price = request.price,
                conditionSummary = request.conditionSummary,
                conditionDetails = request.conditionDetails,
                location = request.location,
                deliveryType = request.deliveryType,
            ).toResponse()

    override fun deleteDraft(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
    ) {
        service.softDelete(publicCode, userId)
    }

    override fun listMine(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @RequestParam(required = false) status: ContractStatus?,
    ): List<ContractListItem> = service.listMyContracts(userId, status).map { it.toListItem() }
}

private fun Contract.toResponse(): ContractResponse =
    ContractResponse(
        publicCode = publicCode,
        status = status,
        disputeState = disputeState,
        deliveryType = deliveryType,
        consentType = consentType,
        title = title,
        price = price,
        conditionSummary = conditionSummary,
        conditionDetails = conditionDetails,
        warrantyPeriodDays = warrantyPeriodDays,
        location = location,
        guardianConsentAt = guardianConsentAt,
        version = version,
        createdAt = requireNotNull(createdAt) { "createdAt 은 @CreationTimestamp 로 채워져야 함" },
        updatedAt = requireNotNull(updatedAt) { "updatedAt 은 @UpdateTimestamp 로 채워져야 함" },
    )

private fun Contract.toListItem(): ContractListItem =
    ContractListItem(
        publicCode = publicCode,
        status = status,
        title = title,
        price = price,
        updatedAt = requireNotNull(updatedAt) { "updatedAt 은 @UpdateTimestamp 로 채워져야 함" },
    )
