package com.trana.dispute.service

import com.trana.contract.adapter.storage.ContractAttachmentStorage
import com.trana.contract.adapter.storage.ContractPdfArchiveStorage
import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractAttachment
import com.trana.contract.repository.ContractAttachmentRepository
import com.trana.contract.service.ContractAccessGuard
import com.trana.dispute.DisputeException
import com.trana.dispute.entity.DisputeStatus
import com.trana.dispute.repository.DisputeRecordRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 증거 패키지 생성 서비스 (W7 분쟁).
 *
 * 구성:
 * - 계약 PDF v3 (archive 버킷, COMPLIANCE 5y 보존)
 * - 첨부 이미지 N장 (attachment 버킷, sortOrder 순)
 *
 * 권한:
 * - 계약 참여자 (creator OR party) + 본인이 활성(REPORTED) 신고 1건 이상 보유
 *
 * 책임 분리:
 * - authorizeAndCollect: 트랜잭션 안 — 권한/데이터 수집 + 일관성 검증
 * - writeZip: 트랜잭션 밖 — S3 stream → ZIP (외부 I/O 가 DB 커넥션 점유 X)
 */
@Service
class EvidencePackageService(
    private val accessGuard: ContractAccessGuard,
    private val disputeRecordRepository: DisputeRecordRepository,
    private val contractAttachmentRepository: ContractAttachmentRepository,
    private val pdfArchiveStorage: ContractPdfArchiveStorage,
    private val attachmentStorage: ContractAttachmentStorage,
) {
    @Transactional(readOnly = true)
    fun authorizeAndCollect(
        publicCode: String,
        userId: Long,
    ): EvidencePackagePayload {
        val contract = accessGuard.loadAccessible(publicCode, userId)
        ensureActiveReportByReporter(contract, userId)
        val pdfS3Key =
            requireNotNull(contract.pdfS3Key) {
                "신고 가능 상태 (SIGNED/COMPLETED) 에서는 PDF v3 가 항상 존재해야 함 (publicCode=$publicCode)"
            }
        val attachments =
            contractAttachmentRepository.findAllByContractIdOrderBySortOrderAsc(contract.id!!)
        return EvidencePackagePayload(
            publicCode = contract.publicCode,
            pdfS3Key = pdfS3Key,
            attachments = attachments,
        )
    }

    fun writeZip(
        payload: EvidencePackagePayload,
        output: OutputStream,
    ) {
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            writePdfEntry(zip, payload.pdfS3Key)
            payload.attachments.forEachIndexed { index, attachment ->
                writeAttachmentEntry(zip, attachment, index)
            }
        }
    }

    private fun writePdfEntry(
        zip: ZipOutputStream,
        pdfS3Key: String,
    ) {
        zip.putNextEntry(ZipEntry("contract.pdf"))
        pdfArchiveStorage.openStream(pdfS3Key).use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun writeAttachmentEntry(
        zip: ZipOutputStream,
        attachment: ContractAttachment,
        index: Int,
    ) {
        val seq = (index + 1).toString().padStart(ATTACHMENT_SEQ_PAD, '0')
        val ext = guessExtension(attachment.contentType, attachment.originalFilename)
        zip.putNextEntry(ZipEntry("attachments/$seq.$ext"))
        attachmentStorage.openStream(attachment.s3Key).use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun ensureActiveReportByReporter(
        contract: Contract,
        userId: Long,
    ) {
        val hasActive =
            disputeRecordRepository.existsByContractIdAndReporterUserIdAndStatus(
                contract.id!!,
                userId,
                DisputeStatus.REPORTED,
            )
        if (!hasActive) {
            throw DisputeException.NoActiveReport(contract.publicCode, userId)
        }
    }

    private fun guessExtension(
        contentType: String?,
        filename: String?,
    ): String =
        when {
            contentType?.contains("png", ignoreCase = true) == true -> "png"
            contentType?.contains("jpeg", ignoreCase = true) == true -> "jpg"
            filename?.endsWith(".png", ignoreCase = true) == true -> "png"
            else -> "jpg"
        }

    companion object {
        private const val ATTACHMENT_SEQ_PAD = 2
    }
}

data class EvidencePackagePayload(
    val publicCode: String,
    val pdfS3Key: String,
    val attachments: List<ContractAttachment>,
)
