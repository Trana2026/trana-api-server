package com.trana.contract.service

import com.trana.common.util.PublicCodeGenerator
import com.trana.contract.ContractException
import com.trana.contract.adapter.storage.ContractPdfArchiveStorage
import com.trana.contract.entity.ConsentType
import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractParty
import com.trana.contract.entity.ContractStatus
import com.trana.contract.entity.ContractStatusLog
import com.trana.contract.entity.DeliveryType
import com.trana.contract.entity.PartyType
import com.trana.contract.repository.ContractPartyRepository
import com.trana.contract.repository.ContractRepository
import com.trana.contract.repository.ContractStatusLogRepository
import com.trana.user.entity.AgeGroup
import com.trana.user.repository.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest

/**
 * 계약 DRAFT 단계 서비스 — 생성/조회/수정/삭제/목록.
 *
 * 권한:
 * - 모든 mutate 는 creatorUserId 본인만
 * - 조회/목록도 본인만 (counterparty 는 W5+ separate endpoint)
 *
 * 상태:
 * - DRAFT 에서만 수정/삭제 허용 (Entity 가 강제)
 *
 * consentType 결정 (가입 흐름과 일치):
 * - ADULT user → NOT_APPLICABLE
 * - MINOR user → GUARDIAN_REQUIRED (보호자 동의는 W4 별도 endpoint)
 */
@Service
@Transactional
@Suppress("TooManyFunctions")
class ContractDraftService(
    private val contractRepository: ContractRepository,
    private val contractPartyRepository: ContractPartyRepository,
    private val statusLogRepository: ContractStatusLogRepository,
    private val userRepository: UserRepository,
    private val publicCodeGenerator: PublicCodeGenerator,
    private val eventPublisher: ApplicationEventPublisher,
    private val pdfRenderer: ContractPdfRenderer,
    private val pdfArchiveStorage: ContractPdfArchiveStorage,
) {
    fun createDraft(
        creatorUserId: Long,
        deliveryType: DeliveryType,
        creatorRole: PartyType,
    ): Contract {
        val user =
            userRepository.findById(creatorUserId).orElseThrow {
                IllegalStateException("계약 작성자 user 조회 실패 (userId=$creatorUserId)")
            }

        val consentType =
            when (user.ageGroup) {
                AgeGroup.MINOR -> ConsentType.GUARDIAN_REQUIRED
                AgeGroup.ADULT -> ConsentType.NOT_APPLICABLE
                null -> throw ContractException.InvalidConsentType("ageGroup 미설정 user 는 계약 생성 불가")
            }

        val contract =
            Contract.createDraft(
                publicCode = publicCodeGenerator.generate(),
                creatorUserId = creatorUserId,
                deliveryType = deliveryType,
                consentType = consentType,
            )
        val saved = contractRepository.save(contract)

        val party =
            ContractParty.create(
                contractId = saved.id!!,
                userId = creatorUserId,
                partyType = creatorRole,
            )
        contractPartyRepository.save(party)

        eventPublisher.publishEvent(
            ContractStatusChangedEvent(
                contractId = saved.id,
                fromStatus = null,
                toStatus = ContractStatus.DRAFT,
                actorUserId = creatorUserId,
                reason = null,
            ),
        )

        return saved
    }

    @Transactional(readOnly = true)
    fun getDraft(
        publicCode: String,
        userId: Long,
    ): Contract = loadOwned(publicCode, userId)

    fun updateDraft(
        publicCode: String,
        userId: Long,
        title: String? = null,
        price: Long? = null,
        conditionSummary: String? = null,
        conditionDetails: String? = null,
        location: String? = null,
        deliveryType: DeliveryType? = null,
    ): Contract {
        val contract = loadOwned(publicCode, userId)
        ensureDraft(contract)
        contract.updateDraft(
            title = title,
            price = price,
            conditionSummary = conditionSummary,
            conditionDetails = conditionDetails,
            location = location,
            deliveryType = deliveryType,
        )
        return contract
    }

    fun softDelete(
        publicCode: String,
        userId: Long,
    ) {
        val contract = loadOwned(publicCode, userId)
        ensureDraft(contract)
        contract.softDelete()
    }

    @Transactional(readOnly = true)
    fun listMyContracts(
        userId: Long,
        status: ContractStatus? = null,
    ): List<Contract> = contractRepository.findAllByCreator(userId, status)

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

    fun transitionToReady(
        publicCode: String,
        userId: Long,
    ): Contract {
        val contract = loadOwned(publicCode, userId)
        ensureDraft(contract)
        validateReadyEligible(contract)

        val pdfBytes = pdfRenderer.render(contract)
        val pdfSha256 = sha256Hex(pdfBytes)
        val pdfS3Key = buildPdfS3Key(publicCode)
        pdfArchiveStorage.uploadPdf(pdfS3Key, pdfBytes)

        val from = contract.status
        contract.markReady(pdfS3Key = pdfS3Key, pdfSha256 = pdfSha256)

        eventPublisher.publishEvent(
            ContractStatusChangedEvent(
                contractId = contract.id!!,
                fromStatus = from,
                toStatus = contract.status,
                actorUserId = userId,
                reason = null,
            ),
        )

        return contract
    }

    fun revertToDraft(
        publicCode: String,
        userId: Long,
    ): Contract {
        val contract = loadOwned(publicCode, userId)
        if (contract.status != ContractStatus.READY) {
            throw ContractException.NotInReadyState(publicCode, contract.status.name)
        }

        val from = contract.status
        contract.markRevertToDraft()

        eventPublisher.publishEvent(
            ContractStatusChangedEvent(
                contractId = contract.id!!,
                fromStatus = from,
                toStatus = contract.status,
                actorUserId = userId,
                reason = null,
            ),
        )

        return contract
    }

    @Transactional(readOnly = true)
    fun listStatusLogs(
        publicCode: String,
        userId: Long,
    ): List<ContractStatusLog> {
        val contract = loadOwned(publicCode, userId)
        return statusLogRepository.findAllByContractIdOrderByChangedAtAsc(contract.id!!)
    }

    @Transactional(readOnly = true)
    fun getPdfDownload(
        publicCode: String,
        userId: Long,
    ): PdfDownloadView {
        val contract = loadOwned(publicCode, userId)
        val s3Key =
            contract.pdfS3Key
                ?: throw ContractException.PdfNotGenerated(publicCode, contract.status.name)
        val sha256 =
            requireNotNull(contract.contentHash) {
                "pdf_s3_key 가 있는데 content_hash 가 null — DB 불변식 위반"
            }
        return PdfDownloadView(
            downloadUrl = pdfArchiveStorage.presignGet(s3Key),
            expiresInSeconds = pdfArchiveStorage.presignedGetTtlSeconds,
            sha256 = sha256,
        )
    }

    @Transactional(readOnly = true)
    fun previewPdf(
        publicCode: String,
        userId: Long,
    ): ByteArray {
        val contract = loadOwned(publicCode, userId)
        ensureDraft(contract)
        validateReadyEligible(contract)
        return pdfRenderer.render(contract)
    }

    private fun ensureDraft(contract: Contract) {
        if (contract.status != ContractStatus.DRAFT) {
            throw ContractException.NotDraft(contract.publicCode, contract.status.name)
        }
    }

    private fun validateReadyEligible(contract: Contract) {
        val missing = mutableListOf<String>()
        if (contract.title == null) missing.add("title")
        if (contract.price == null) missing.add("price")
        if (contract.conditionSummary == null) missing.add("conditionSummary")
        if (contract.conditionDetails == null) missing.add("conditionDetails")
        if (missing.isNotEmpty()) {
            throw ContractException.NotReadyEligible(contract.publicCode, missing.joinToString(", "))
        }
        if (contract.consentType == ConsentType.GUARDIAN_REQUIRED && contract.guardianConsentAt == null) {
            throw ContractException.GuardianConsentRequired(contract.publicCode)
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun buildPdfS3Key(publicCode: String): String = "contracts/$publicCode/pdf.pdf"
}

data class PdfDownloadView(
    val downloadUrl: String,
    val expiresInSeconds: Long,
    val sha256: String,
)
