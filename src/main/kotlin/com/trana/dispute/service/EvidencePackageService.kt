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
import tools.jackson.databind.ObjectMapper
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 증거 패키지 생성 서비스 (W7 분쟁).
 *
 * ZIP 구성:
 * - `evidence.json` — 증거 요약(감사 추적): 계약 메타 + 무결성 해시(content/final) + 당사자·본인확인(PASS)
 *   + 양측 서명(시각/IP/UA/PDF해시/서명이미지 해시) + 약관 동의 + 상태 전이 이력 + (미성년) 고지확인 + 첨부 해시
 * - 계약 PDF v3 (archive 버킷, COMPLIANCE 5y 보존)
 * - 첨부 이미지 N장 (attachment 버킷, sortOrder 순)
 *
 * 권한:
 * - 계약 참여자 (creator OR party) + 본인이 활성(REPORTED) 신고 1건 이상 보유
 *
 * 책임 분리:
 * - authorizeAndCollect: 트랜잭션 안 — 권한/데이터 수집 + 요약(manifest) 구성 (엔티티 참조 없는 순수 데이터로 변환)
 * - writeZip: 트랜잭션 밖 — manifest 직렬화 + S3 stream → ZIP (외부 I/O 가 DB 커넥션 점유 X)
 */
@Service
class EvidencePackageService(
    private val accessGuard: ContractAccessGuard,
    private val disputeRecordRepository: DisputeRecordRepository,
    private val contractAttachmentRepository: ContractAttachmentRepository,
    private val manifestBuilder: EvidenceManifestBuilder,
    private val pdfArchiveStorage: ContractPdfArchiveStorage,
    private val attachmentStorage: ContractAttachmentStorage,
    private val objectMapper: ObjectMapper,
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
            manifest = manifestBuilder.build(contract, attachments),
        )
    }

    fun writeZip(
        payload: EvidencePackagePayload,
        output: OutputStream,
    ) {
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            writeManifestEntry(zip, payload.manifest)
            writePdfEntry(zip, payload.pdfS3Key)
            payload.attachments.forEachIndexed { index, attachment ->
                writeAttachmentEntry(zip, attachment, index)
            }
        }
    }

    private fun writeManifestEntry(
        zip: ZipOutputStream,
        manifest: EvidenceManifest,
    ) {
        zip.putNextEntry(ZipEntry("evidence.json"))
        zip.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest))
        zip.closeEntry()
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
        zip.putNextEntry(ZipEntry(EvidenceManifestBuilder.attachmentEntryName(index, attachment)))
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
}

data class EvidencePackagePayload(
    val publicCode: String,
    val pdfS3Key: String,
    val attachments: List<ContractAttachment>,
    val manifest: EvidenceManifest,
)

/** 증거 요약(evidence.json) 스키마 — 엔티티 참조 없는 직렬화 전용 데이터. */
data class EvidenceManifest(
    val generatedAt: Instant,
    val publicCode: String,
    val title: String?,
    val price: Long?,
    val status: String,
    val disputeState: String,
    val integrity: IntegrityInfo,
    val parties: List<PartyEvidence>,
    val signatures: List<SignatureEvidence>,
    val consents: List<ConsentEvidence>,
    val statusHistory: List<StatusTransitionEvidence>,
    val minorDisclosures: List<MinorDisclosureEvidence>,
    val attachments: List<AttachmentEvidence>,
)

data class IntegrityInfo(
    val contentHash: String?,
    val finalHash: String?,
    val finalHashScheme: String,
    val pdfVersion: Int,
    val pdfGeneratedAt: Instant?,
)

data class PartyEvidence(
    val userId: Long,
    val partyType: String,
    val name: String?,
    val birthDate: String?,
    val phoneMasked: String?,
    val ageGroup: String?,
    val passVerified: Boolean,
    val passVerifiedAt: Instant?,
    val ciHash: String?,
)

data class SignatureEvidence(
    val partyType: String,
    val userId: Long,
    val signedAt: Instant?,
    val signerIp: String?,
    val signerUserAgent: String?,
    val pdfVersionAtSign: Int,
    val pdfSha256AtSign: String?,
    val signatureImageSha256: String,
)

data class ConsentEvidence(
    val userId: Long,
    val partyType: String?,
    val termType: String?,
    val termVersion: String,
    val consentedAt: Instant?,
    val consenterIp: String?,
)

data class StatusTransitionEvidence(
    val fromStatus: String?,
    val toStatus: String,
    val actorUserId: Long?,
    val reason: String?,
    val changedAt: Instant?,
)

data class MinorDisclosureEvidence(
    val partyUserId: Long,
    val templateVersion: String,
    val disclosedAt: Instant?,
    val confirmedAt: Instant?,
    val ip: String?,
    val userAgent: String?,
)

data class AttachmentEvidence(
    val file: String,
    val sha256: String,
    val contentType: String?,
    val uploadedAt: Instant?,
)
