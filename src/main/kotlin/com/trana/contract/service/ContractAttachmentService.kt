package com.trana.contract.service

import com.trana.contract.ContractException
import com.trana.contract.adapter.storage.ContractAttachmentStorage
import com.trana.contract.adapter.storage.PresignedUpload
import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractAttachment
import com.trana.contract.entity.ContractStatus
import com.trana.contract.repository.ContractAttachmentRepository
import com.trana.contract.repository.ContractRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 계약 첨부 사진 서비스 — presigned URL 발급 → 메타 등록 → 목록/삭제.
 *
 * 흐름 (2단계):
 * 1. issueUploadUrl(): presigned PUT URL + s3Key 발급 (TTL 10분)
 * 2. 클라이언트가 S3 로 직접 PUT
 * 3. register(): 업로드 완료 후 메타 영속화
 *
 * 제약:
 * - DRAFT 만 추가/삭제 가능
 * - creator 본인만
 * - 최대 7장 (count 기반 cap)
 *
 * 삭제 순서: DB 먼저 → S3 (S3 실패해도 tx 롤백으로 DB 복구 → 일관성 유지)
 *
 * TODO(W4 후):
 * - 업로드 후 register 호출 안 되는 케이스 cleanup task
 * - sizeBytes > maxAttachmentSizeBytes 차단 (현재 미적용, NotFound/MaxAttachments 외 ErrorCode 추가 필요)
 */
@Service
@Transactional
class ContractAttachmentService(
    private val contractRepository: ContractRepository,
    private val attachmentRepository: ContractAttachmentRepository,
    private val storage: ContractAttachmentStorage,
) {
    /**
     * 1단계 — presigned PUT URL 발급.
     *
     * Service 가 s3Key 결정 (클라이언트가 임의 경로 못 지정).
     */
    fun issueUploadUrl(
        publicCode: String,
        userId: Long,
        contentType: String,
    ): PresignedUpload {
        val contract = loadOwnedDraft(publicCode, userId)
        ensureCapacity(contract)
        val s3Key = buildS3Key(publicCode)
        return storage.presignPut(s3Key, contentType)
    }

    /**
     * 2단계 — 클라이언트가 S3 PUT 완료 후 호출. 메타 영속화.
     *
     * sortOrder 는 등록 시점 count (0-based 자동 증가).
     */
    fun register(
        publicCode: String,
        userId: Long,
        s3Key: String,
        contentType: String?,
        sizeBytes: Long?,
        originalFilename: String?,
    ): ContractAttachment {
        val contract = loadOwnedDraft(publicCode, userId)
        ensureCapacity(contract)
        val sha256 = storage.computeSha256(s3Key)
        val nextOrder = attachmentRepository.countByContractId(contract.id!!).toInt()
        val attachment =
            ContractAttachment.create(
                contractId = contract.id,
                s3Key = s3Key,
                originalFilename = originalFilename,
                contentType = contentType,
                sizeBytes = sizeBytes,
                sha256 = sha256,
                sortOrder = nextOrder,
            )
        return attachmentRepository.save(attachment)
    }

    @Transactional(readOnly = true)
    fun list(
        publicCode: String,
        userId: Long,
    ): List<ContractAttachment> {
        val contract = loadOwned(publicCode, userId)
        return attachmentRepository.findAllByContractIdOrderBySortOrderAsc(contract.id!!)
    }

    fun delete(
        publicCode: String,
        userId: Long,
        attachmentId: Long,
    ) {
        val contract = loadOwnedDraft(publicCode, userId)
        val attachment =
            attachmentRepository.findById(attachmentId).orElseThrow {
                ContractException.AttachmentNotFound(attachmentId)
            }
        if (attachment.contractId != contract.id) {
            throw ContractException.AttachmentNotFound(attachmentId)
        }
        attachmentRepository.delete(attachment)
        storage.delete(attachment.s3Key)
    }

    private fun loadOwned(
        publicCode: String,
        userId: Long,
    ): Contract {
        val contract =
            contractRepository.findByPublicCodeAndDeletedAtIsNull(publicCode)
                ?: throw ContractException.NotFound(publicCode)
        if (contract.creatorUserId != userId) {
            throw ContractException.NotOwner(publicCode, userId)
        }
        return contract
    }

    private fun loadOwnedDraft(
        publicCode: String,
        userId: Long,
    ): Contract {
        val contract = loadOwned(publicCode, userId)
        if (contract.status != ContractStatus.IN_PROGRESS && contract.status != ContractStatus.DRAFT) {
            throw ContractException.NotDraft(publicCode, contract.status.name)
        }
        return contract
    }

    private fun ensureCapacity(contract: Contract) {
        val count = attachmentRepository.countByContractId(contract.id!!)
        if (count >= MAX_ATTACHMENTS) {
            throw ContractException.MaxAttachments(contract.publicCode, count.toInt())
        }
    }

    private fun buildS3Key(publicCode: String): String = "contracts/$publicCode/attachments/${UUID.randomUUID()}"

    companion object {
        private const val MAX_ATTACHMENTS = 7
    }
}
