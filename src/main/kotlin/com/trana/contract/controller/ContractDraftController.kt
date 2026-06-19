package com.trana.contract.controller

import com.trana.contract.dto.ConfirmCompletionResponse
import com.trana.contract.dto.ContractListItem
import com.trana.contract.dto.ContractPdfDownloadResponse
import com.trana.contract.dto.ContractResponse
import com.trana.contract.dto.ContractStatusLogResponse
import com.trana.contract.dto.CreateContractDraftRequest
import com.trana.contract.dto.CreatorSignRequest
import com.trana.contract.dto.CreatorSignResponse
import com.trana.contract.dto.ReceiverSignRequest
import com.trana.contract.dto.ReceiverSignResponse
import com.trana.contract.dto.RequestRevisionRequest
import com.trana.contract.dto.RiskSignalsResponse
import com.trana.contract.dto.ShareContractRequest
import com.trana.contract.dto.UpdateContractDraftRequest
import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractStatus
import com.trana.contract.entity.ContractStatusLog
import com.trana.contract.service.ContractDraftService
import com.trana.contract.service.ContractListView
import com.trana.contract.service.ContractSigningService
import com.trana.contract.service.ContractStatusService
import com.trana.contract.service.PdfDownloadView
import com.trana.contract.service.RiskSignalsCalculator
import io.swagger.v3.oas.annotations.Parameter
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/contracts")
@Suppress("TooManyFunctions")
class ContractDraftController(
    private val service: ContractDraftService,
    private val statusService: ContractStatusService,
    private val signingService: ContractSigningService,
    private val riskSignalsCalculator: RiskSignalsCalculator,
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
                requestedConsentType = request.consentType,
            )
        return contract.toResponse()
    }

    override fun getDetail(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
    ): ContractResponse {
        val contract = service.getDetail(publicCode, userId)
        val riskSignals = riskSignalsCalculator.calculate(contract, userId)
        return contract.toResponse(riskSignals)
    }

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
                tradingPlatform = request.tradingPlatform,
                deliveryType = request.deliveryType,
                warrantyPeriodDays = request.warrantyPeriodDays,
                creatorRole = request.creatorRole,
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
        @RequestParam(required = false) query: String?,
    ): List<ContractListItem> = service.listMyContracts(userId, status, query).map { it.toListItem() }

    override fun markReady(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
    ): ContractResponse = statusService.transitionToReady(publicCode, userId).toResponse()

    override fun share(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
        @RequestBody @Valid request: ShareContractRequest,
    ): ContractResponse =
        statusService
            .share(
                publicCode = publicCode,
                userId = userId,
                receiverName = request.receiverName,
                receiverPhone = request.receiverPhone,
            ).toResponse()

    override fun requestRevision(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
        @RequestBody @Valid request: RequestRevisionRequest,
    ): ContractResponse =
        statusService
            .requestRevision(
                publicCode = publicCode,
                requesterUserId = userId,
                titleReason = request.titleReason,
                priceReason = request.priceReason,
                conditionSummaryReason = request.conditionSummaryReason,
                conditionDetailsReason = request.conditionDetailsReason,
            ).toResponse()

    override fun acceptInvitation(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable token: String,
    ): ContractResponse = signingService.acceptInvitation(token, userId).toResponse()

    override fun revertToDraft(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
    ): ContractResponse = statusService.revertToDraft(publicCode, userId).toResponse()

    override fun statusLogs(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
    ): List<ContractStatusLogResponse> = statusService.listStatusLogs(publicCode, userId).map { it.toResponse() }

    override fun previewPdf(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
    ): ResponseEntity<ByteArray> {
        val bytes = service.previewPdf(publicCode, userId)
        return ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, """attachment; filename="contract-preview.pdf"""")
            .body(bytes)
    }

    override fun receiverSign(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
        @RequestBody @Valid request: ReceiverSignRequest,
        httpRequest: HttpServletRequest,
    ): ReceiverSignResponse =
        signingService
            .receiverSign(
                publicCode = publicCode,
                userId = userId,
                signatureBase64 = request.signatureBase64,
                agreedTermIds = request.agreedTermIds,
                signerIp = httpRequest.remoteAddr,
                signerUserAgent = httpRequest.getHeader("User-Agent"),
            ).toResponse()

    override fun creatorSign(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
        @RequestBody @Valid request: CreatorSignRequest,
        httpRequest: HttpServletRequest,
    ): CreatorSignResponse =
        signingService
            .creatorSign(
                publicCode = publicCode,
                userId = userId,
                signatureBase64 = request.signatureBase64,
                agreedTermIds = request.agreedTermIds,
                signerIp = httpRequest.remoteAddr,
                signerUserAgent = httpRequest.getHeader("User-Agent"),
            ).toResponse()

    override fun confirmCompletion(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
    ): ConfirmCompletionResponse =
        signingService.confirmCompletion(publicCode = publicCode, userId = userId).toResponse()

    override fun pdfDownload(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
    ): ContractPdfDownloadResponse = statusService.getPdfDownload(publicCode, userId).toResponse()
}

private fun Contract.toResponse(riskSignals: RiskSignalsResponse? = null): ContractResponse =
    ContractResponse(
        publicCode = publicCode,
        status = status,
        disputeState = disputeState,
        deliveryType = deliveryType,
        consentType = consentType,
        tradingPlatform = tradingPlatform,
        title = title,
        price = price,
        conditionSummary = conditionSummary,
        conditionDetails = conditionDetails,
        warrantyPeriodDays = warrantyPeriodDays,
        guardianConsentAt = guardianConsentAt,
        version = version,
        contentHash = contentHash,
        createdAt = requireNotNull(createdAt) { "createdAt 은 @CreationTimestamp 로 채워져야 함" },
        updatedAt = requireNotNull(updatedAt) { "updatedAt 은 @UpdateTimestamp 로 채워져야 함" },
        riskSignals = riskSignals,
    )

private fun ContractListView.toListItem(): ContractListItem =
    ContractListItem(
        publicCode = contract.publicCode,
        status = contract.status,
        title = contract.title,
        price = contract.price,
        isCreator = isCreator,
        myRole = myRole,
        attachmentCount = attachmentCount,
        firstAttachmentUrl = firstAttachmentUrl,
        updatedAt = requireNotNull(contract.updatedAt) { "updatedAt 은 @UpdateTimestamp 로 채워져야 함" },
    )

private fun ContractStatusLog.toResponse(): ContractStatusLogResponse =
    ContractStatusLogResponse(
        id = requireNotNull(id) { "ContractStatusLog.id 는 영속화 후 채워짐" },
        fromStatus = fromStatus,
        toStatus = toStatus,
        actorUserId = actorUserId,
        reason = reason,
        changedAt = requireNotNull(changedAt) { "changedAt 은 @CreationTimestamp 로 채워짐" },
    )

private fun PdfDownloadView.toResponse(): ContractPdfDownloadResponse =
    ContractPdfDownloadResponse(
        downloadUrl = downloadUrl,
        expiresInSeconds = expiresInSeconds,
        sha256 = sha256,
    )

private fun ContractSigningService.ReceiverSignView.toResponse(): ReceiverSignResponse =
    ReceiverSignResponse(
        publicCode = publicCode,
        status = status,
        pdfVersion = pdfVersion,
        receiverSignedAt = receiverSignedAt,
    )

private fun ContractSigningService.CreatorSignView.toResponse(): CreatorSignResponse =
    CreatorSignResponse(
        publicCode = publicCode,
        status = status,
        pdfVersion = pdfVersion,
        creatorSignedAt = creatorSignedAt,
    )

private fun ContractSigningService.ConfirmCompletionView.toResponse(): ConfirmCompletionResponse =
    ConfirmCompletionResponse(
        publicCode = publicCode,
        status = status,
        sellerCompletedAt = sellerCompletedAt,
        buyerCompletedAt = buyerCompletedAt,
        completedAt = completedAt,
    )
