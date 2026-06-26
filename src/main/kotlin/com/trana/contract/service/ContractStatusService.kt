package com.trana.contract.service

import com.trana.common.util.TokenGenerator
import com.trana.contract.ContractException
import com.trana.contract.adapter.storage.ContractPdfArchiveStorage
import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractInvitation
import com.trana.contract.entity.ContractRevisionRequest
import com.trana.contract.entity.ContractStatus
import com.trana.contract.entity.ContractStatusLog
import com.trana.contract.repository.ContractInvitationRepository
import com.trana.contract.repository.ContractRepository
import com.trana.contract.repository.ContractRevisionRequestRepository
import com.trana.contract.repository.ContractStatusLogRepository
import com.trana.user.entity.AgeGroup
import com.trana.user.entity.User
import com.trana.user.entity.UserStatus
import com.trana.user.repository.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest

/**
 * 계약 상태 전이 / 공유 / 서명 통합 서비스.
 *
 * 흐름 (W5~W6):
 * - DRAFT → READY      : transitionToReady (PDF v1 생성, W5)
 * - READY → DRAFT      : revertToDraft (W5)
 * - READY → SHARED     : share (수신자 이름/phone + 카톡 알림톡, W6)
 * - SHARED → RECEIVER_SIGNED : (W6 #31, 수신자 서명)
 * - RECEIVER_SIGNED → SIGNED : (W6 #32, 생성자 최종)
 * - 어느 단계 → CANCELLED    : (W6 #33)
 *
 * ContractDraftService 와 분리 — 그쪽은 DRAFT 작성/수정/삭제만 (CRUD).
 */
@Service
@Transactional
@Suppress("LongParameterList", "TooManyFunctions")
class ContractStatusService(
    private val accessGuard: ContractAccessGuard,
    private val statusLogRepository: ContractStatusLogRepository,
    private val invitationRepository: ContractInvitationRepository,
    private val tokenGenerator: TokenGenerator,
    private val contractRepository: ContractRepository,
    private val revisionRequestRepository: ContractRevisionRequestRepository,
    private val contractAlimtalkDispatcher: ContractAlimtalkDispatcher,
    private val userRepository: UserRepository,
    private val pdfRenderer: ContractPdfRenderer,
    private val pdfArchiveStorage: ContractPdfArchiveStorage,
    private val eventPublisher: ApplicationEventPublisher,
    private val committer: ContractStatusCommitter,
) {
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun transitionToReady(
        publicCode: String,
        userId: Long,
    ): Contract {
        // 1. preview — committer 의 readOnly tx
        val preview = committer.loadTransitionToReadyPreview(publicCode, userId)

        // 2. 외부 I/O — 트랜잭션 밖 (refactor d)
        val pdfBytes = pdfRenderer.render(preview)
        val pdfSha256 = sha256Hex(pdfBytes)
        val pdfS3Key = buildPdfS3Key(publicCode)
        pdfArchiveStorage.uploadPdf(pdfS3Key, pdfBytes)

        // 3. commit — committer 의 rw tx
        return committer.commitTransitionToReady(publicCode, userId, pdfS3Key, pdfSha256)
    }

    fun revertToDraft(
        publicCode: String,
        userId: Long,
    ): Contract {
        val contract = accessGuard.loadOwned(publicCode, userId)
        if (contract.status != ContractStatus.READY && contract.status != ContractStatus.REVISION_REQUESTED) {
            throw ContractException.NotInReadyState(publicCode, contract.status.name)
        }
        val from = contract.status
        contract.markRevertToDraft()
        val reason =
            when (from) {
                ContractStatus.REVISION_REQUESTED -> "수신자 수정 요청 → 수정 모드 진입"
                else -> null
            }
        publishStatusChanged(contract, from, userId, reason)
        return contract
    }

    fun share(
        publicCode: String,
        userId: Long,
        receiverName: String,
        receiverPhone: String,
    ): Contract {
        val contract = accessGuard.loadOwned(publicCode, userId)
        if (contract.status != ContractStatus.READY) {
            throw ContractException.NotInReadyState(publicCode, contract.status.name)
        }

        val invitation =
            ContractInvitation.create(
                contractId = contract.id!!,
                token = tokenGenerator.generateContractInvitation(),
                receiverName = receiverName,
                receiverPhone = receiverPhone,
            )
        invitationRepository.save(invitation)

        val from = contract.status
        contract.markShared()
        publishStatusChanged(contract, from, userId, null)

        contractAlimtalkDispatcher.sendNewContract(contract, userId, invitation)
        return contract
    }

    @Suppress("ThrowsCount")
    fun requestRevision(
        publicCode: String,
        requesterUserId: Long,
        deliveryTypeReason: String? = null,
        tradingPlatformReason: String? = null,
        titleReason: String? = null,
        priceReason: String? = null,
        conditionSummaryReason: String? = null,
        conditionDetailsReason: String? = null,
    ): Contract {
        val contract = accessGuard.loadAccessible(publicCode, requesterUserId)
        if (contract.creatorUserId == requesterUserId) {
            throw ContractException.NotAccessible(publicCode, requesterUserId)
        }
        if (contract.status != ContractStatus.SHARED) {
            throw ContractException.NotInSharedState(publicCode, contract.status.name)
        }

        val revisionRequest =
            ContractRevisionRequest.create(
                contractId = contract.id!!,
                requesterUserId = requesterUserId,
                deliveryTypeReason = deliveryTypeReason,
                tradingPlatformReason = tradingPlatformReason,
                titleReason = titleReason,
                priceReason = priceReason,
                conditionSummaryReason = conditionSummaryReason,
                conditionDetailsReason = conditionDetailsReason,
            )
        revisionRequestRepository.save(revisionRequest)

        val from = contract.status
        contract.markRevisionRequested()
        publishStatusChanged(contract, from, requesterUserId, "수신자 수정 요청")

        contractAlimtalkDispatcher.sendRevisionRequested(
            contract,
            requesterUserId,
            deliveryTypeReason,
            tradingPlatformReason,
            titleReason,
            priceReason,
            conditionSummaryReason,
            conditionDetailsReason,
        )

        return contract
    }

    @Transactional(readOnly = true)
    fun getLatestRevisionRequest(
        publicCode: String,
        userId: Long,
    ): ContractRevisionRequest {
        val contract = accessGuard.loadAccessible(publicCode, userId)
        return revisionRequestRepository
            .findAllByContractIdOrderByRequestedAtDesc(contract.id!!)
            .firstOrNull()
            ?: throw ContractException.RevisionRequestNotFound(publicCode)
    }

    private fun toPartyRenderInfo(
        user: User,
        signatureBase64: String?,
    ): PartyRenderInfo =
        PartyRenderInfo(
            name = user.name ?: "(unknown)",
            birthDate = user.birthDate ?: "(unknown)",
            phone = user.phone ?: "(unknown)",
            signatureBase64 = signatureBase64,
        )

    @Transactional(readOnly = true)
    fun listStatusLogs(
        publicCode: String,
        userId: Long,
    ): List<ContractStatusLog> {
        val contract = accessGuard.loadAccessible(publicCode, userId)
        return statusLogRepository.findAllByContractIdOrderByChangedAtAsc(contract.id!!)
    }

    @Transactional(readOnly = true)
    fun getPdfDownload(
        publicCode: String,
        userId: Long,
    ): PdfDownloadView {
        val contract = accessGuard.loadAccessible(publicCode, userId)
        val s3Key =
            contract.pdfS3Key
                ?: throw ContractException.PdfNotGenerated(publicCode, contract.status.name)
        val sha256 =
            requireNotNull(contract.contentHash) {
                "pdf_s3_key 가 있는데 content_hash 가 null — DB 불변식 위반"
            }
        val disposition =
            when (contract.status) {
                ContractStatus.COMPLETED -> ContractPdfArchiveStorage.Disposition.ATTACHMENT
                else -> ContractPdfArchiveStorage.Disposition.INLINE
            }
        val filename = "contract-$publicCode.pdf"
        return PdfDownloadView(
            downloadUrl = pdfArchiveStorage.presignGet(s3Key, disposition, filename),
            expiresInSeconds = pdfArchiveStorage.presignedGetTtlSeconds,
            sha256 = sha256,
        )
    }

    private fun publishStatusChanged(
        contract: Contract,
        from: ContractStatus,
        actorUserId: Long,
        reason: String?,
    ) {
        eventPublisher.publishEvent(
            ContractStatusChangedEvent(
                contractId = contract.id!!,
                fromStatus = from,
                toStatus = contract.status,
                actorUserId = actorUserId,
                reason = reason,
            ),
        )
    }

    @Suppress("ThrowsCount")
    private fun loadActiveInvitationOnSharedContract(token: String): ActiveInvitationContext {
        val invitation =
            invitationRepository.findByToken(token)
                ?: throw ContractException.InvitationNotFound(token)
        if (!invitation.isActive()) {
            throw ContractException.InvitationExpired(token)
        }
        val contract =
            contractRepository.findById(invitation.contractId).orElseThrow {
                ContractException.NotFound("contractId=${invitation.contractId}")
            }
        if (contract.status != ContractStatus.SHARED) {
            throw ContractException.NotInSharedState(contract.publicCode, contract.status.name)
        }
        return ActiveInvitationContext(invitation, contract)
    }

    private fun validateUserReady(userId: Long) {
        val user =
            userRepository.findById(userId).orElseThrow {
                IllegalStateException("user 조회 실패 (userId=$userId)")
            }
        if (user.status != UserStatus.ACTIVE) {
            throw ContractException.UserNotReady(userId, "user.status=${user.status}")
        }
        if (user.ageGroup == AgeGroup.MINOR && user.guardianVerifiedAt == null) {
            throw ContractException.UserNotReady(userId, "미성년 보호자 검증 미완료")
        }
    }

    private data class ActiveInvitationContext(
        val invitation: ContractInvitation,
        val contract: Contract,
    )

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun buildPdfS3Key(publicCode: String): String = "contracts/$publicCode/pdf.pdf"
}
