package com.trana.contract.controller

import com.trana.contract.adapter.storage.PresignedUpload
import com.trana.contract.dto.AttachmentResponse
import com.trana.contract.dto.PresignAttachmentRequest
import com.trana.contract.dto.PresignAttachmentResponse
import com.trana.contract.dto.RegisterAttachmentRequest
import com.trana.contract.entity.ContractAttachment
import com.trana.contract.service.ContractAttachmentService
import io.swagger.v3.oas.annotations.Parameter
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/contracts/{publicCode}/attachments")
class ContractAttachmentController(
    private val service: ContractAttachmentService,
) : ContractAttachmentApi {
    override fun presign(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
        @RequestBody request: PresignAttachmentRequest,
    ): PresignAttachmentResponse =
        service
            .issueUploadUrl(
                publicCode = publicCode,
                userId = userId,
                contentType = request.contentType,
            ).toResponse()

    override fun register(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
        @RequestBody request: RegisterAttachmentRequest,
    ): AttachmentResponse =
        service
            .register(
                publicCode = publicCode,
                userId = userId,
                s3Key = request.s3Key,
                contentType = request.contentType,
                sizeBytes = request.sizeBytes,
                originalFilename = request.originalFilename,
            ).toResponse()

    override fun list(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
    ): List<AttachmentResponse> = service.list(publicCode, userId).map { it.toResponse() }

    override fun delete(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable publicCode: String,
        @PathVariable attachmentId: Long,
    ) {
        service.delete(publicCode, userId, attachmentId)
    }
}

private fun PresignedUpload.toResponse(): PresignAttachmentResponse =
    PresignAttachmentResponse(
        uploadUrl = uploadUrl,
        s3Key = s3Key,
        expiresAt = expiresAt,
    )

private fun ContractAttachment.toResponse(): AttachmentResponse =
    AttachmentResponse(
        id = requireNotNull(id) { "ContractAttachment.id 는 영속화 후 채워짐" },
        s3Key = s3Key,
        originalFilename = originalFilename,
        contentType = contentType,
        sizeBytes = sizeBytes,
        sha256 = sha256,
        sortOrder = sortOrder,
        uploadedAt = requireNotNull(uploadedAt) { "uploadedAt 은 @CreationTimestamp 로 채워짐" },
    )
