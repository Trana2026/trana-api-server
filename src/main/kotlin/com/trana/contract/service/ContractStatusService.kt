package com.trana.contract.service

import com.trana.common.util.TokenGenerator
import com.trana.common.web.WebUrlBuilder
import com.trana.contract.ContractException
import com.trana.contract.adapter.kakao.ContractCompletedMessage
import com.trana.contract.adapter.kakao.KakaoAlimtalkClient
import com.trana.contract.adapter.kakao.NewContractMessage
import com.trana.contract.adapter.kakao.ReceiverSignedMessage
import com.trana.contract.adapter.kakao.RevisionRequestedMessage
import com.trana.contract.adapter.storage.ContractPdfArchiveStorage
import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractInvitation
import com.trana.contract.entity.ContractParty
import com.trana.contract.entity.ContractRevisionRequest
import com.trana.contract.entity.ContractStatus
import com.trana.contract.entity.ContractStatusLog
import com.trana.contract.entity.PartyType
import com.trana.contract.repository.ContractConsentRepository
import com.trana.contract.repository.ContractInvitationRepository
import com.trana.contract.repository.ContractPartyRepository
import com.trana.contract.repository.ContractRepository
import com.trana.contract.repository.ContractRevisionRequestRepository
import com.trana.contract.repository.ContractSignatureRepository
import com.trana.contract.repository.ContractStatusLogRepository
import com.trana.user.entity.AgeGroup
import com.trana.user.entity.User
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
    private val kakaoAlimtalkClient: KakaoAlimtalkClient,
    private val userRepository: UserRepository,
    private val contractPartyRepository: ContractPartyRepository,
    private val pdfRenderer: ContractPdfRenderer,
    private val pdfArchiveStorage: ContractPdfArchiveStorage,
    private val eventPublisher: ApplicationEventPublisher,
    private val contractSignatureRepository: ContractSignatureRepository,
    private val contractConsentRepository: ContractConsentRepository,
    private val committer: ContractStatusCommitter,
    private val webUrlBuilder: WebUrlBuilder,
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

        sendRevisionRequestedAlimtalk(
            contract,
            requesterUserId,
            titleReason,
            priceReason,
            conditionSummaryReason,
            conditionDetailsReason,
        )

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
        // 1. preview — committer 의 readOnly tx
        val preview = committer.loadReceiverSignPreview(publicCode, userId, agreedTermIds)

        // 2. 외부 I/O (트랜잭션 밖) — PDF v2 렌더링 + S3 PUT (refactor d)
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

        // 3. commit — committer 의 rw tx
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

        // 4. 알림톡 (트랜잭션 밖)
        sendReceiverSignedAlimtalk(result.contract, preview.receiverName)

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
        // 1. preview — committer 의 readOnly tx
        val preview = committer.loadCreatorSignPreview(publicCode, userId, agreedTermIds)

        // 2. 외부 I/O (트랜잭션 밖) — PDF v3 렌더링 (양측 박스 채움) + S3 PUT (refactor d)
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

        // 3. commit — committer 의 rw tx
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

        // 4. 알림톡 (트랜잭션 밖) — 양측에 거래 체결 완료 통보
        sendCompletedAlimtalkBoth(result.contract, result.creator, result.receiver)

        return CreatorSignView(
            publicCode = result.contract.publicCode,
            status = result.contract.status,
            pdfVersion = result.contract.version,
            creatorSignedAt = result.creatorSignedAt,
        )
    }

    /**
     * 거래 완료 확인 (W7).
     *
     * - 양측 (SELLER + BUYER) 각자 호출 → contract_parties.completed_at 채움
     * - 두 번째 클릭 시점에 contract.markCompleted() 자동 호출 (SIGNED → COMPLETED + completed_at)
     * - 보증기간(3일) 시작 기준 = contract.completed_at
     * - 멱등 X — 본인이 이미 클릭했으면 409 (AlreadyCompletedByParty)
     *
     * 흐름:
     * 1. accessGuard.loadAccessible 로 권한 확인 (creator OR party 만)
     * 2. status != SIGNED → NotInSignedState (DRAFT/READY/SHARED/RECEIVER_SIGNED/COMPLETED 등 모두 차단)
     * 3. 본인의 ContractParty 조회 → completedAt 검사 → markCompleted()
     * 4. 양측 ContractParty 모두 completedAt != null 이면 contract.markCompleted() + status log
     *
     * 알림톡: W7 분쟁 흐름과 함께 결정 (현재는 status log 만).
     */
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
        if (myParty.completedAt != null) {
            throw ContractException.AlreadyCompletedByParty(publicCode, userId)
        }
        myParty.markCompleted()

        val parties = contractPartyRepository.findAllByContractId(contract.id!!)
        val bothCompleted = parties.size == 2 && parties.all { it.completedAt != null }
        if (bothCompleted) {
            val from = contract.status
            contract.markCompleted()
            publishStatusChanged(contract, from, userId, "양측 거래 완료 확정")
        }

        val seller = parties.firstOrNull { it.partyType == PartyType.SELLER }
        val buyer = parties.firstOrNull { it.partyType == PartyType.BUYER }
        return ConfirmCompletionView(
            publicCode = contract.publicCode,
            status = contract.status,
            sellerCompletedAt = seller?.completedAt,
            buyerCompletedAt = buyer?.completedAt,
            completedAt = contract.completedAt,
        )
    }

    private fun sendCompletedAlimtalkBoth(
        contract: Contract,
        creator: User,
        receiver: User,
    ) {
        val downloadUrl = webUrlBuilder.contractPdf(contract.publicCode)
        listOf(creator, receiver).forEach { recipient ->
            val recipientName = recipient.name ?: recipient.nickname ?: "Trana 사용자"
            val recipientPhone = recipient.phone ?: "(unknown)"
            kakaoAlimtalkClient.sendCompleted(
                ContractCompletedMessage(
                    recipientPhone = recipientPhone,
                    recipientName = recipientName,
                    contractTitle = contract.title ?: "(제목 없음)",
                    price = requireNotNull(contract.price) { "price 누락 (COMPLETED 전이 후 invariant 위반)" },
                    completedAt =
                        requireNotNull(
                            contract.completedAt,
                        ) { "completedAt 누락 (COMPLETED 전이 후 invariant 위반)" },
                    downloadUrl = downloadUrl,
                ),
            )
        }
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

    private fun sendReceiverSignedAlimtalk(
        contract: Contract,
        receiverName: String,
    ) {
        val creator =
            userRepository.findById(contract.creatorUserId).orElseThrow {
                IllegalStateException("계약 작성자 조회 실패 (userId=${contract.creatorUserId})")
            }
        val creatorName = creator.name ?: creator.nickname ?: "Trana 사용자"
        val creatorPhone = creator.phone ?: "(unknown)"
        val reviewUrl = webUrlBuilder.contractDetail(contract.publicCode)
        kakaoAlimtalkClient.sendReceiverSigned(
            ReceiverSignedMessage(
                creatorPhone = creatorPhone,
                creatorName = creatorName,
                receiverName = receiverName,
                contractTitle = contract.title ?: "(제목 없음)",
                price = requireNotNull(contract.price) { "price 누락 (RECEIVER_SIGNED 전이 후 invariant 위반)" },
                reviewUrl = reviewUrl,
            ),
        )
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
        val invitationUrl = webUrlBuilder.contractInvitation(invitation.token)
        kakaoAlimtalkClient.sendNewContract(
            NewContractMessage(
                receiverPhone = invitation.receiverPhone,
                receiverName = invitation.receiverName,
                sellerName = sellerName,
                contractTitle = contract.title ?: "(제목 없음)",
                price = requireNotNull(contract.price) { "price 누락 (SHARED 전이 후 invariant 위반)" },
                invitationUrl = invitationUrl,
            ),
        )
    }

    private fun sendRevisionRequestedAlimtalk(
        contract: Contract,
        requesterUserId: Long,
        titleReason: String?,
        priceReason: String?,
        conditionSummaryReason: String?,
        conditionDetailsReason: String?,
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
        val reviewUrl = webUrlBuilder.contractDetail(contract.publicCode)
        val revisionReason =
            buildRevisionReason(titleReason, priceReason, conditionSummaryReason, conditionDetailsReason)
        kakaoAlimtalkClient.sendRevisionRequested(
            RevisionRequestedMessage(
                creatorPhone = creatorPhone,
                creatorName = creatorName,
                contractTitle = contract.title ?: "(제목 없음)",
                requesterName = requesterName,
                price = requireNotNull(contract.price) { "price 누락 (REVISION_REQUESTED 전이 후 invariant 위반)" },
                revisionReason = revisionReason,
                reviewUrl = reviewUrl,
            ),
        )
    }

    private fun buildRevisionReason(
        titleReason: String?,
        priceReason: String?,
        conditionSummaryReason: String?,
        conditionDetailsReason: String?,
    ): String =
        buildList {
            titleReason?.let { add("제목: $it") }
            priceReason?.let { add("가격: $it") }
            conditionSummaryReason?.let { add("조건 요약: $it") }
            conditionDetailsReason?.let { add("조건 상세: $it") }
        }.joinToString("\n").ifBlank { "(사유 없음)" }

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
