package com.trana.contract.service

import com.trana.common.util.ContractInvitationTokenGenerator
import com.trana.contract.ContractException
import com.trana.contract.adapter.kakao.KakaoAlimtalkClient
import com.trana.contract.adapter.kakao.NewContractMessage
import com.trana.contract.adapter.kakao.RevisionRequestedMessage
import com.trana.contract.adapter.storage.ContractPdfArchiveStorage
import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractInvitation
import com.trana.contract.entity.ContractParty
import com.trana.contract.entity.ContractRevisionRequest
import com.trana.contract.entity.ContractStatus
import com.trana.contract.entity.ContractStatusLog
import com.trana.contract.entity.PartyType
import com.trana.contract.repository.ContractInvitationRepository
import com.trana.contract.repository.ContractPartyRepository
import com.trana.contract.repository.ContractRepository
import com.trana.contract.repository.ContractRevisionRequestRepository
import com.trana.contract.repository.ContractStatusLogRepository
import com.trana.user.entity.AgeGroup
import com.trana.user.entity.UserStatus
import com.trana.user.repository.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
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
    private val invitationTokenGenerator: ContractInvitationTokenGenerator,
    private val contractRepository: ContractRepository,
    private val revisionRequestRepository: ContractRevisionRequestRepository,
    private val kakaoAlimtalkClient: KakaoAlimtalkClient,
    private val userRepository: UserRepository,
    private val contractPartyRepository: ContractPartyRepository,
    private val pdfRenderer: ContractPdfRenderer,
    private val pdfArchiveStorage: ContractPdfArchiveStorage,
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun transitionToReady(
        publicCode: String,
        userId: Long,
    ): Contract {
        val contract = accessGuard.loadOwned(publicCode, userId)
        accessGuard.ensureDraft(contract)
        accessGuard.validateReadyEligible(contract)

        val pdfBytes = pdfRenderer.render(contract)
        val pdfSha256 = sha256Hex(pdfBytes)
        val pdfS3Key = buildPdfS3Key(publicCode)
        pdfArchiveStorage.uploadPdf(pdfS3Key, pdfBytes)

        val from = contract.status
        contract.markReady(pdfS3Key = pdfS3Key, pdfSha256 = pdfSha256)
        publishStatusChanged(contract, from, userId, null)
        return contract
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
                token = invitationTokenGenerator.generate(),
                receiverName = receiverName,
                receiverPhone = receiverPhone,
            )
        invitationRepository.save(invitation)

        val from = contract.status
        contract.markShared()
        publishStatusChanged(contract, from, userId, null)

        sendNewContractAlimtalk(contract, userId, invitation)
        return contract
    }

    @Suppress("ThrowsCount")
    fun requestRevision(
        publicCode: String,
        requesterUserId: Long,
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
                titleReason = titleReason,
                priceReason = priceReason,
                conditionSummaryReason = conditionSummaryReason,
                conditionDetailsReason = conditionDetailsReason,
            )
        revisionRequestRepository.save(revisionRequest)

        val from = contract.status
        contract.markRevisionRequested()
        publishStatusChanged(contract, from, requesterUserId, "수신자 수정 요청")

        sendRevisionRequestedAlimtalk(contract, requesterUserId)
        return contract
    }

    @Suppress("ThrowsCount")
    fun acceptInvitation(
        token: String,
        userId: Long,
    ): Contract {
        val (invitation, contract) = loadActiveInvitationOnSharedContract(token)

        if (contract.creatorUserId == userId) {
            throw ContractException.NotAccessible(contract.publicCode, userId)
        }

        validateUserReady(userId)

        val existing = contractPartyRepository.findFirstByContractIdAndUserId(contract.id!!, userId)
        if (existing != null) {
            return contract
        }

        val creatorParty =
            contractPartyRepository.findFirstByContractIdAndUserId(contract.id, contract.creatorUserId)
                ?: error("creator party 없음 — 데이터 무결성 위반 (contractId=${contract.id})")
        val receiverPartyType =
            when (creatorParty.partyType) {
                PartyType.SELLER -> PartyType.BUYER
                PartyType.BUYER -> PartyType.SELLER
            }

        val party =
            ContractParty.create(
                contractId = contract.id,
                userId = userId,
                partyType = receiverPartyType,
            )
        party.markValidated()
        contractPartyRepository.save(party)

        invitation.markUsed(userId)
        return contract
    }

    @Transactional(readOnly = true)
    fun listStatusLogs(
        publicCode: String,
        userId: Long,
    ): List<ContractStatusLog> {
        val contract = accessGuard.loadOwned(publicCode, userId)
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

    private fun sendNewContractAlimtalk(
        contract: Contract,
        sellerUserId: Long,
        invitation: ContractInvitation,
    ) {
        val seller =
            userRepository.findById(sellerUserId).orElseThrow {
                IllegalStateException("계약 작성자 user 조회 실패 (userId=$sellerUserId)")
            }
        val sellerName = seller.name ?: seller.nickname ?: "Trana 사용자"
        val invitationUrl = "$INVITATION_BASE_URL/contracts/invitations/${invitation.token}"
        kakaoAlimtalkClient.sendNewContract(
            NewContractMessage(
                receiverPhone = invitation.receiverPhone,
                receiverName = invitation.receiverName,
                sellerName = sellerName,
                contractTitle = contract.title ?: "(제목 없음)",
                invitationUrl = invitationUrl,
            ),
        )
    }

    private fun sendRevisionRequestedAlimtalk(
        contract: Contract,
        requesterUserId: Long,
    ) {
        val creator =
            userRepository.findById(contract.creatorUserId).orElseThrow {
                IllegalStateException("계약 작성자 조회 실패 (userId=${contract.creatorUserId})")
            }
        val requester =
            userRepository.findById(requesterUserId).orElseThrow {
                IllegalStateException("수정 요청자 조회 실패 (userId=$requesterUserId)")
            }
        val creatorName = creator.name ?: creator.nickname ?: "Trana 사용자"
        val creatorPhone = creator.phone ?: "(unknown)"
        val requesterName = requester.name ?: requester.nickname ?: "Trana 사용자"
        val reviewUrl = "$INVITATION_BASE_URL/contracts/${contract.publicCode}"
        kakaoAlimtalkClient.sendRevisionRequested(
            RevisionRequestedMessage(
                creatorPhone = creatorPhone,
                creatorName = creatorName,
                contractTitle = contract.title ?: "(제목 없음)",
                requesterName = requesterName,
                reviewUrl = reviewUrl,
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

    companion object {
        // TODO(W6): ConfigurationProperties 로 분리 (dev/prod URL 분기, BSP 준비 시점)
        private const val INVITATION_BASE_URL = "https://trana.app"
    }
}
