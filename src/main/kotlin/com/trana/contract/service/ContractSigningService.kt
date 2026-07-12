package com.trana.contract.service

import com.trana.contract.ContractException
import com.trana.contract.adapter.storage.ContractPdfArchiveStorage
import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractInvitation
import com.trana.contract.entity.ContractParty
import com.trana.contract.entity.ContractStatus
import com.trana.contract.entity.PartyType
import com.trana.contract.repository.ContractInvitationRepository
import com.trana.contract.repository.ContractPartyRepository
import com.trana.contract.repository.ContractRepository
import com.trana.user.entity.AgeGroup
import com.trana.user.entity.UserStatus
import com.trana.user.repository.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant

/**
 * 계약 서명 흐름 통합 서비스.
 *
 * 흐름 (W6~W7):
 * - SHARED → 수신자 수락 (acceptInvitation)
 * - SHARED → RECEIVER_SIGNED (receiverSign, PDF v2, 알림톡)
 * - RECEIVER_SIGNED → SIGNED (creatorSign, PDF v3, 알림톡)
 * - SIGNED → COMPLETED (구매자 confirmCompletion 단독 확정, W7 — 즉시 전이)
 *
 * #102 refactor Phase B — ContractStatusService 에서 4 메서드 통째 추출.
 * 알림톡 발송은 ContractAlimtalkDispatcher 위임 (Phase A 에서 추출).
 */
@Service
@Transactional
@Suppress("LongParameterList")
class ContractSigningService(
    private val accessGuard: ContractAccessGuard,
    private val contractRepository: ContractRepository,
    private val invitationRepository: ContractInvitationRepository,
    private val userRepository: UserRepository,
    private val contractPartyRepository: ContractPartyRepository,
    private val pdfRenderer: ContractPdfRenderer,
    private val pdfArchiveStorage: ContractPdfArchiveStorage,
    private val committer: ContractStatusCommitter,
    private val contractAlimtalkDispatcher: ContractAlimtalkDispatcher,
    private val minorDisclosureService: MinorDisclosureConfirmationService,
    private val eventPublisher: ApplicationEventPublisher,
) {
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

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Suppress("LongParameterList")
    fun receiverSign(
        publicCode: String,
        userId: Long,
        signatureBase64: String,
        agreedTermIds: List<Long>,
        signerIp: String?,
        signerUserAgent: String?,
    ): ReceiverSignView {
        val preview = committer.loadReceiverSignPreview(publicCode, userId, agreedTermIds)
        minorDisclosureService.requireConfirmedIfMinorCounterparty(preview.contract, viewerUserId = userId)

        val partyInfo =
            PartyRenderInfo(
                name = preview.receiverName,
                birthDate = preview.receiverBirthDate,
                phone = preview.receiverPhone,
                signatureBase64 = signatureBase64,
            )
        val renderInput =
            ContractPdfRenderInput(
                contract = preview.contract,
                seller = if (preview.partyType == PartyType.SELLER) partyInfo else null,
                buyer = if (preview.partyType == PartyType.BUYER) partyInfo else null,
            )
        val pdfBytes = pdfRenderer.render(renderInput)
        val pdfSha256 = sha256Hex(pdfBytes)
        val pdfS3Key = buildPdfS3Key(publicCode)
        pdfArchiveStorage.uploadPdf(pdfS3Key, pdfBytes)

        val result =
            committer.commitReceiverSign(
                publicCode = publicCode,
                userId = userId,
                signatureBase64 = signatureBase64,
                expectedTerms = preview.expectedTerms,
                signerIp = signerIp,
                signerUserAgent = signerUserAgent,
                pdfS3Key = pdfS3Key,
                pdfSha256 = pdfSha256,
            )

        contractAlimtalkDispatcher.sendReceiverSigned(result.contract, preview.receiverName)

        return ReceiverSignView(
            publicCode = result.contract.publicCode,
            status = result.contract.status,
            pdfVersion = result.contract.version,
            receiverSignedAt = result.receiverSignedAt,
        )
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Suppress("LongParameterList")
    fun creatorSign(
        publicCode: String,
        userId: Long,
        signatureBase64: String,
        agreedTermIds: List<Long>,
        signerIp: String?,
        signerUserAgent: String?,
    ): CreatorSignView {
        val preview = committer.loadCreatorSignPreview(publicCode, userId, agreedTermIds)
        minorDisclosureService.requireConfirmedIfMinorCounterparty(preview.contract, viewerUserId = userId)

        val creatorInfo =
            PartyRenderInfo(
                name = preview.creator.name,
                birthDate = preview.creator.birthDate,
                phone = preview.creator.phone,
                signatureBase64 = signatureBase64,
            )
        val receiverInfo =
            PartyRenderInfo(
                name = preview.receiver.name,
                birthDate = preview.receiver.birthDate,
                phone = preview.receiver.phone,
                signatureBase64 = preview.receiverSignatureBase64,
            )
        val renderInput =
            ContractPdfRenderInput(
                contract = preview.contract,
                seller = if (preview.creatorPartyType == PartyType.SELLER) creatorInfo else receiverInfo,
                buyer = if (preview.creatorPartyType == PartyType.BUYER) creatorInfo else receiverInfo,
            )
        val pdfBytes = pdfRenderer.render(renderInput)
        val pdfSha256 = sha256Hex(pdfBytes)
        val pdfS3Key = buildPdfS3Key(publicCode)
        pdfArchiveStorage.uploadPdf(pdfS3Key, pdfBytes)

        val result =
            committer.commitCreatorSign(
                publicCode = publicCode,
                userId = userId,
                signatureBase64 = signatureBase64,
                expectedTerms = preview.expectedTerms,
                signerIp = signerIp,
                signerUserAgent = signerUserAgent,
                pdfS3Key = pdfS3Key,
                pdfSha256 = pdfSha256,
            )

        contractAlimtalkDispatcher.sendCompleted(result.contract, result.creator, result.receiver)

        return CreatorSignView(
            publicCode = result.contract.publicCode,
            status = result.contract.status,
            pdfVersion = result.contract.version,
            creatorSignedAt = result.creatorSignedAt,
        )
    }

    @Retryable(
        retryFor = [ObjectOptimisticLockingFailureException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 50),
    )
    @Suppress("ThrowsCount")
    fun confirmCompletion(
        publicCode: String,
        userId: Long,
    ): ConfirmCompletionView {
        val contract = accessGuard.loadAccessible(publicCode, userId)
        if (contract.status != ContractStatus.SIGNED) {
            throw ContractException.NotInSignedState(publicCode, contract.status.name)
        }

        val myParty =
            contractPartyRepository.findFirstByContractIdAndUserId(contract.id!!, userId)
                ?: throw ContractException.NotAccessible(publicCode, userId)
        if (myParty.partyType != PartyType.BUYER) {
            throw ContractException.NotBuyer(publicCode, userId)
        }

        val parties = contractPartyRepository.findAllByContractId(contract.id!!)
        val seller =
            parties.firstOrNull { it.partyType == PartyType.SELLER }
                ?: error("seller party 없음 (contractId=${contract.id})")
        val buyer =
            parties.firstOrNull { it.partyType == PartyType.BUYER }
                ?: error("buyer party 없음 (contractId=${contract.id})")

        seller.markCompleted()
        buyer.markCompleted()

        val from = contract.status
        contract.markCompleted()
        publishStatusChanged(contract, from, userId, "구매자 거래 완료 확정")

        return ConfirmCompletionView(
            publicCode = contract.publicCode,
            status = contract.status,
            sellerCompletedAt = seller.completedAt,
            buyerCompletedAt = buyer.completedAt,
            completedAt = contract.completedAt,
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

    data class ReceiverSignView(
        val publicCode: String,
        val status: ContractStatus,
        val pdfVersion: Int,
        val receiverSignedAt: Instant,
    )

    data class CreatorSignView(
        val publicCode: String,
        val status: ContractStatus,
        val pdfVersion: Int,
        val creatorSignedAt: Instant,
    )

    data class ConfirmCompletionView(
        val publicCode: String,
        val status: ContractStatus,
        val sellerCompletedAt: Instant?,
        val buyerCompletedAt: Instant?,
        val completedAt: Instant?,
    )
}
