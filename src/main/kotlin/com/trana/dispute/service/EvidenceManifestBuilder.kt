package com.trana.dispute.service

import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractAttachment
import com.trana.contract.repository.ContractConsentRepository
import com.trana.contract.repository.ContractPartyRepository
import com.trana.contract.repository.ContractSignatureRepository
import com.trana.contract.repository.ContractStatusLogRepository
import com.trana.contract.repository.MinorDisclosureConfirmationRepository
import com.trana.contract.service.ContractFinalHasher
import com.trana.identity.entity.VerificationPurpose
import com.trana.identity.entity.VerificationStatus
import com.trana.identity.repository.IdentityVerificationRepository
import com.trana.terms.repository.TermsVersionRepository
import com.trana.user.entity.User
import com.trana.user.repository.UserRepository
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * 증거 요약(evidence.json) 구성 — DB 에 흩어진 WORM audit 을 직렬화 전용 데이터로 모은다.
 *
 * PII 최소화: CI 는 해시만, 전화번호 마스킹, 서명 이미지 원문 대신 SHA-256 만 수록.
 * 첨부 파일명은 [EvidencePackageService] 의 ZIP entry 와 일치해야 하므로 여기서 한 번만 계산한다.
 */
@Component
class EvidenceManifestBuilder(
    private val contractPartyRepository: ContractPartyRepository,
    private val contractSignatureRepository: ContractSignatureRepository,
    private val contractConsentRepository: ContractConsentRepository,
    private val contractStatusLogRepository: ContractStatusLogRepository,
    private val minorDisclosureRepository: MinorDisclosureConfirmationRepository,
    private val identityVerificationRepository: IdentityVerificationRepository,
    private val termsVersionRepository: TermsVersionRepository,
    private val userRepository: UserRepository,
) {
    fun build(
        contract: Contract,
        attachments: List<ContractAttachment>,
    ): EvidenceManifest {
        val contractId = contract.id!!
        val parties = contractPartyRepository.findAllByContractId(contractId)
        val users = parties.associate { it.userId to userRepository.findById(it.userId).orElse(null) }
        val partyTypeByUser = parties.associate { it.userId to it.partyType.name }
        val termTypeById =
            contractConsentRepository
                .findAllByContractIdOrderByConsentedAtAsc(contractId)
                .map { it.termId }
                .distinct()
                .let { ids -> termsVersionRepository.findAllById(ids).associate { it.id!! to it.type.name } }

        return EvidenceManifest(
            generatedAt = Instant.now(),
            publicCode = contract.publicCode,
            title = contract.title,
            price = contract.price,
            status = contract.status.name,
            disputeState = contract.disputeState.name,
            integrity =
                IntegrityInfo(
                    contentHash = contract.contentHash,
                    finalHash = contract.finalHash,
                    finalHashScheme = ContractFinalHasher.SCHEME,
                    pdfVersion = contract.version,
                    pdfGeneratedAt = contract.pdfGeneratedAt,
                ),
            parties = parties.map { toPartyEvidence(it.userId, it.partyType.name, users[it.userId]) },
            signatures = buildSignatures(contractId),
            consents = buildConsents(contractId, partyTypeByUser, termTypeById),
            statusHistory = buildStatusHistory(contractId),
            minorDisclosures = buildMinorDisclosures(contractId),
            attachments = buildAttachments(attachments),
        )
    }

    private fun toPartyEvidence(
        userId: Long,
        partyType: String,
        user: User?,
    ): PartyEvidence {
        val pass =
            identityVerificationRepository.findFirstByUserIdAndPurposeAndStatusOrderByCreatedAtDesc(
                userId = userId,
                purpose = VerificationPurpose.SIGNUP,
                status = VerificationStatus.SUCCESS,
            )
        return PartyEvidence(
            userId = userId,
            partyType = partyType,
            name = user?.name,
            birthDate = user?.birthDate,
            phoneMasked = user?.phone?.let { maskPhone(it) },
            ageGroup = user?.ageGroup?.name,
            passVerified = pass != null,
            passVerifiedAt = pass?.createdAt,
            ciHash = pass?.ciHash,
        )
    }

    private fun buildSignatures(contractId: Long): List<SignatureEvidence> =
        contractSignatureRepository
            .findAllByContractIdOrderBySignedAtAsc(contractId)
            .map { sig ->
                SignatureEvidence(
                    partyType = sig.partyType.name,
                    userId = sig.userId,
                    signedAt = sig.signedAt,
                    signerIp = sig.signerIp,
                    signerUserAgent = sig.signerUserAgent,
                    pdfVersionAtSign = sig.pdfVersionAtSign,
                    pdfSha256AtSign = sig.pdfSha256AtSign,
                    signatureImageSha256 = ContractFinalHasher.sha256Hex(sig.signatureData),
                )
            }

    private fun buildConsents(
        contractId: Long,
        partyTypeByUser: Map<Long, String>,
        termTypeById: Map<Long, String>,
    ): List<ConsentEvidence> =
        contractConsentRepository
            .findAllByContractIdOrderByConsentedAtAsc(contractId)
            .map { c ->
                ConsentEvidence(
                    userId = c.userId,
                    partyType = partyTypeByUser[c.userId],
                    termType = termTypeById[c.termId],
                    termVersion = c.termVersion,
                    consentedAt = c.consentedAt,
                    consenterIp = c.consenterIp,
                )
            }

    private fun buildStatusHistory(contractId: Long): List<StatusTransitionEvidence> =
        contractStatusLogRepository
            .findAllByContractIdOrderByChangedAtAsc(contractId)
            .map { log ->
                StatusTransitionEvidence(
                    fromStatus = log.fromStatus?.name,
                    toStatus = log.toStatus.name,
                    actorUserId = log.actorUserId,
                    reason = log.reason,
                    changedAt = log.changedAt,
                )
            }

    private fun buildMinorDisclosures(contractId: Long): List<MinorDisclosureEvidence> =
        minorDisclosureRepository
            .findAllByContractId(contractId)
            .map { d ->
                MinorDisclosureEvidence(
                    partyUserId = d.partyUserId,
                    templateVersion = d.templateVersion,
                    disclosedAt = d.disclosedAt,
                    confirmedAt = d.confirmedAt,
                    ip = d.ip,
                    userAgent = d.userAgent,
                )
            }

    private fun buildAttachments(attachments: List<ContractAttachment>): List<AttachmentEvidence> =
        attachments.mapIndexed { index, a ->
            AttachmentEvidence(
                file = attachmentEntryName(index, a),
                sha256 = a.sha256,
                contentType = a.contentType,
                uploadedAt = a.uploadedAt,
            )
        }

    companion object {
        private const val ATTACHMENT_SEQ_PAD = 2
        private const val MIN_PHONE_LENGTH = 10
        private const val MASK_START = 4
        private const val MASK_END = 7

        /** ZIP 내 첨부 경로 — manifest 와 실제 entry 가 동일 규칙을 공유. */
        fun attachmentEntryName(
            index: Int,
            attachment: ContractAttachment,
        ): String {
            val seq = (index + 1).toString().padStart(ATTACHMENT_SEQ_PAD, '0')
            return "attachments/$seq.${guessExtension(attachment.contentType, attachment.originalFilename)}"
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

        /** 010-1234-5678 → 010-****-5678 (PII 마스킹) */
        private fun maskPhone(phone: String): String =
            if (phone.length < MIN_PHONE_LENGTH) {
                "****"
            } else {
                phone.replaceRange(MASK_START..MASK_END, "****")
            }
    }
}
