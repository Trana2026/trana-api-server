package com.trana.contract.service

import com.trana.common.util.PublicCodeGenerator
import com.trana.contract.ContractException
import com.trana.contract.adapter.storage.ContractAttachmentStorage
import com.trana.contract.entity.ConsentType
import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractParty
import com.trana.contract.entity.ContractStatus
import com.trana.contract.entity.DeliveryType
import com.trana.contract.entity.PartyType
import com.trana.contract.repository.ContractAttachmentRepository
import com.trana.contract.repository.ContractPartyRepository
import com.trana.contract.repository.ContractRepository
import com.trana.user.entity.AgeGroup
import com.trana.user.repository.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 계약 DRAFT 단계 서비스 — 생성/조회/수정/삭제/목록/미리보기 (CRUD).
 *
 * 권한:
 * - 모든 mutate 는 creatorUserId 본인만
 * - 조회/목록도 본인만 (counterparty 는 W6+ separate endpoint)
 *
 * 상태:
 * - DRAFT 에서만 수정/삭제 허용 (Entity 가 강제)
 *
 * 상태 전이 / 공유 / 서명은 [ContractStatusService] 책임 (W6 분리).
 *
 * consentType 결정 (가입 흐름과 일치):
 * - ADULT user → NOT_APPLICABLE
 * - MINOR user → GUARDIAN_REQUIRED (보호자 동의는 W4 별도 endpoint)
 */
@Service
@Transactional
class ContractDraftService(
    private val contractRepository: ContractRepository,
    private val contractPartyRepository: ContractPartyRepository,
    private val userRepository: UserRepository,
    private val publicCodeGenerator: PublicCodeGenerator,
    private val eventPublisher: ApplicationEventPublisher,
    private val pdfRenderer: ContractPdfRenderer,
    private val accessGuard: ContractAccessGuard,
    private val contractAttachmentRepository: ContractAttachmentRepository,
    private val attachmentStorage: ContractAttachmentStorage,
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
                toStatus = ContractStatus.IN_PROGRESS,
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
    ): Contract = accessGuard.loadOwned(publicCode, userId)

    fun updateDraft(
        publicCode: String,
        userId: Long,
        title: String? = null,
        price: Long? = null,
        conditionSummary: String? = null,
        conditionDetails: String? = null,
        deliveryType: DeliveryType? = null,
    ): Contract {
        val contract = accessGuard.loadOwned(publicCode, userId)
        accessGuard.ensureEditable(contract)
        val fromStatus = contract.status
        contract.updateDraft(
            title = title,
            price = price,
            conditionSummary = conditionSummary,
            conditionDetails = conditionDetails,
            deliveryType = deliveryType,
        )
        if (contract.status != fromStatus) {
            eventPublisher.publishEvent(
                ContractStatusChangedEvent(
                    contractId = contract.id!!,
                    fromStatus = fromStatus,
                    toStatus = contract.status,
                    actorUserId = userId,
                    reason = null,
                ),
            )
        }
        return contract
    }

    fun softDelete(
        publicCode: String,
        userId: Long,
    ) {
        val contract = accessGuard.loadOwned(publicCode, userId)
        accessGuard.ensureEditable(contract)
        contract.softDelete()
    }

    @Transactional(readOnly = true)
    fun listMyContracts(
        userId: Long,
        status: ContractStatus? = null,
        query: String? = null,
    ): List<ContractListView> {
        val normalizedQuery = query?.takeIf { it.isNotBlank() }?.trim()
        val contracts = contractRepository.findAllByPartyUserId(userId, status, normalizedQuery)
        if (contracts.isEmpty()) return emptyList()

        val contractIds = contracts.mapNotNull { it.id }
        val myParties =
            contractPartyRepository
                .findAllByUserIdAndContractIdIn(userId, contractIds)
                .associateBy { it.contractId }
        val attachmentsByContractId =
            contractAttachmentRepository
                .findAllByContractIdIn(contractIds)
                .groupBy { it.contractId }

        return contracts.map { contract ->
            val contractId = contract.id!!
            val party = myParties[contractId] ?: error("party 없음 (contractId=$contractId, userId=$userId)")
            val attachments = attachmentsByContractId[contractId].orEmpty()
            val first = attachments.minByOrNull { it.sortOrder }
            ContractListView(
                contract = contract,
                myRole = party.partyType,
                attachmentCount = attachments.size,
                firstAttachmentUrl = first?.let { attachmentStorage.presignGet(it.s3Key) },
            )
        }
    }

    @Transactional(readOnly = true)
    fun previewPdf(
        publicCode: String,
        userId: Long,
    ): ByteArray {
        val contract = accessGuard.loadOwned(publicCode, userId)
        accessGuard.ensureDraft(contract)
        accessGuard.validateReadyEligible(contract)
        return pdfRenderer.render(contract)
    }
}

data class PdfDownloadView(
    val downloadUrl: String,
    val expiresInSeconds: Long,
    val sha256: String,
)

data class ContractListView(
    val contract: Contract,
    val myRole: PartyType,
    val attachmentCount: Int,
    val firstAttachmentUrl: String?,
)
