package com.trana.contract.service

import com.trana.common.util.TokenGenerator
import com.trana.contract.ContractException
import com.trana.contract.adapter.storage.ContractAttachmentStorage
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
 * 미성년 계약 생성 정책:
 * - MINOR + guardianVerifiedAt == null → 403 (가입 보호자 PASS 미완료)
 * - 계약 단계 보호자 동의는 항상 선택 — 별도 endpoint (/guardian-consent/request)
 */
@Service
@Transactional
class ContractDraftService(
    private val contractRepository: ContractRepository,
    private val contractPartyRepository: ContractPartyRepository,
    private val userRepository: UserRepository,
    private val tokenGenerator: TokenGenerator,
    private val eventPublisher: ApplicationEventPublisher,
    private val pdfRenderer: ContractPdfRenderer,
    private val accessGuard: ContractAccessGuard,
    private val contractAttachmentRepository: ContractAttachmentRepository,
    private val attachmentStorage: ContractAttachmentStorage,
) {
    fun createDraft(creatorUserId: Long): Contract {
        val user =
            userRepository.findById(creatorUserId).orElseThrow {
                IllegalStateException("계약 작성자 user 조회 실패 (userId=$creatorUserId)")
            }
        if (user.ageGroup == AgeGroup.MINOR && user.guardianVerifiedAt == null) {
            throw ContractException.GuardianNotVerified(requireNotNull(user.id))
        }

        val contract =
            Contract.createDraft(
                publicCode = tokenGenerator.generatePublicCode(),
                creatorUserId = creatorUserId,
            )
        val saved = contractRepository.save(contract)

        eventPublisher.publishEvent(
            ContractStatusChangedEvent(
                contractId = saved.id!!,
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

    /**
     * 상세 조회 — 양측(creator OR party) 모두 가능.
     * - SHARED / RECEIVER_SIGNED 등 진행 단계에서 receiver 의 detail 진입 허용
     * - getDraft 는 creator 전용 (DRAFT 편집용) — 명확 분리
     */
    @Transactional(readOnly = true)
    fun getDetail(
        publicCode: String,
        userId: Long,
    ): Contract = accessGuard.loadAccessible(publicCode, userId)

    fun updateDraft(
        publicCode: String,
        userId: Long,
        title: String? = null,
        price: Long? = null,
        conditionSummary: String? = null,
        conditionDetails: String? = null,
        tradingPlatform: String? = null,
        deliveryType: DeliveryType? = null,
        warrantyPeriodDays: Int? = null,
        creatorRole: PartyType? = null,
    ): Contract {
        val contract = accessGuard.loadOwned(publicCode, userId)
        accessGuard.ensureUpdatable(contract)

        if (creatorRole != null) {
            val existing = contractPartyRepository.findFirstByContractIdAndUserId(contract.id!!, userId)
            if (existing != null) {
                throw ContractException.RoleAlreadySet(publicCode)
            }
            val party =
                ContractParty.create(
                    contractId = contract.id,
                    userId = userId,
                    partyType = creatorRole,
                )
            contractPartyRepository.save(party)
        }

        val fromStatus = contract.status
        contract.updateDraft(
            title = title,
            price = price,
            conditionSummary = conditionSummary,
            conditionDetails = conditionDetails,
            tradingPlatform = tradingPlatform,
            deliveryType = deliveryType,
            warrantyPeriodDays = warrantyPeriodDays,
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
        val visible =
            if (status == null) {
                contracts.filter { it.status != ContractStatus.CANCELLED }
            } else {
                contracts
            }
        if (visible.isEmpty()) return emptyList()

        val contractIds = visible.mapNotNull { it.id }
        val myParties =
            contractPartyRepository
                .findAllByUserIdAndContractIdIn(userId, contractIds)
                .associateBy { it.contractId }
        val attachmentsByContractId =
            contractAttachmentRepository
                .findAllByContractIdIn(contractIds)
                .groupBy { it.contractId }

        return visible.map { contract ->
            val contractId = contract.id!!
            val party = myParties[contractId]
            val attachments = attachmentsByContractId[contractId].orEmpty()
            val first = attachments.minByOrNull { it.sortOrder }
            ContractListView(
                contract = contract,
                isCreator = contract.creatorUserId == userId,
                myRole = party?.partyType,
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
        accessGuard.ensurePreviewable(contract)
        accessGuard.validateReadyEligible(contract)
        return pdfRenderer.render(ContractPdfRenderInput(contract))
    }
}

data class PdfDownloadView(
    val downloadUrl: String,
    val expiresInSeconds: Long,
    val sha256: String,
)

data class ContractListView(
    val contract: Contract,
    val isCreator: Boolean,
    val myRole: PartyType?,
    val attachmentCount: Int,
    val firstAttachmentUrl: String?,
)
