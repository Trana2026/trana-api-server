package com.trana.contract.service

import com.trana.common.util.ContractInvitationTokenGenerator
import com.trana.contract.ContractException
import com.trana.contract.adapter.kakao.KakaoAlimtalkClient
import com.trana.contract.adapter.kakao.NewContractMessage
import com.trana.contract.adapter.storage.ContractPdfArchiveStorage
import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractInvitation
import com.trana.contract.entity.ContractStatus
import com.trana.contract.entity.ContractStatusLog
import com.trana.contract.repository.ContractInvitationRepository
import com.trana.contract.repository.ContractStatusLogRepository
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
@Suppress("LongParameterList")
class ContractStatusService(
    private val accessGuard: ContractAccessGuard,
    private val statusLogRepository: ContractStatusLogRepository,
    private val invitationRepository: ContractInvitationRepository,
    private val invitationTokenGenerator: ContractInvitationTokenGenerator,
    private val kakaoAlimtalkClient: KakaoAlimtalkClient,
    private val userRepository: UserRepository,
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
        if (contract.status != ContractStatus.READY) {
            throw ContractException.NotInReadyState(publicCode, contract.status.name)
        }
        val from = contract.status
        contract.markRevertToDraft()
        publishStatusChanged(contract, from, userId, null)
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
        val contract = accessGuard.loadOwned(publicCode, userId)
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
