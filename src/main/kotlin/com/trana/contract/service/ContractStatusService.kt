package com.trana.contract.service
import com.trana.analytics.AnalyticsEvent
import com.trana.analytics.AnalyticsEvents
import com.trana.analytics.AnalyticsTracker
import com.trana.common.util.TokenGenerator
import com.trana.contract.ContractException
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
    private val contractPartyRepository: ContractPartyRepository,
    private val pdfRenderer: ContractPdfRenderer,
    private val pdfArchiveStorage: ContractPdfArchiveStorage,
    private val eventPublisher: ApplicationEventPublisher,
    private val committer: ContractStatusCommitter,
    private val analyticsTracker: AnalyticsTracker,
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
        val contract = committer.commitTransitionToReady(publicCode, userId, pdfS3Key, pdfSha256)

        // EVT-026 contract_draft_generated — 초안(PDF) 생성·저장 성공(서버 기준). PII 금지.
        analyticsTracker.track(
            AnalyticsEvent(
                name = AnalyticsEvents.CONTRACT_DRAFT_GENERATED,
                userId = userId,
                properties =
                    mapOf(
                        "contract_id" to contract.publicCode,
                        "transaction_type" to contract.deliveryType?.name?.lowercase(),
                        "warranty_days" to contract.warrantyPeriodDays,
                    ),
            ),
        )
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

    @Suppress("TooGenericExceptionCaught", "LongMethod")
    fun share(
        publicCode: String,
        userId: Long,
        receiverName: String?,
        receiverPhone: String?,
        receiverCode: String?,
    ): Contract {
        try {
            val contract = accessGuard.loadOwned(publicCode, userId)
            if (contract.status != ContractStatus.READY) {
                throw ContractException.NotInReadyState(publicCode, contract.status.name)
            }

            val target = resolveShareTarget(userId, receiverName, receiverPhone, receiverCode)

            val invitation =
                ContractInvitation.create(
                    contractId = contract.id!!,
                    token = tokenGenerator.generateContractInvitation(),
                    receiverName = target.name,
                    receiverPhone = target.phone,
                )
            invitationRepository.save(invitation)

            // 코드 공유: 수신자를 party 로 직등록 (초대토큰은 audit 로만 잔존, 링크는 계약 상세 사용)
            target.recipientUserId?.let { addRecipientPartyIfAbsent(contract, it) }

            val from = contract.status
            contract.markShared()
            publishStatusChanged(contract, from, userId, null)

            contractAlimtalkDispatcher.sendNewContract(contract, userId, invitation)

            // EVT-033 contract_share_completed (GA4 share) — 계약 요청 생성+발송 성공
            analyticsTracker.track(
                AnalyticsEvent(
                    name = AnalyticsEvents.CONTRACT_SHARE_COMPLETED,
                    gaEventName = "share",
                    userId = userId,
                    properties =
                        mapOf(
                            "contract_id" to contract.publicCode,
                            "share_method" to if (target.recipientUserId != null) "code" else "phone",
                            "actor_role" to "creator",
                        ),
                ),
            )
            return contract
        } catch (e: RuntimeException) {
            // EVT-034 contract_share_failed — 요청 생성/발송 실패
            analyticsTracker.track(
                AnalyticsEvent(
                    name = AnalyticsEvents.CONTRACT_SHARE_FAILED,
                    userId = userId,
                    properties =
                        mapOf(
                            "contract_id" to publicCode,
                            "error_code" to (e::class.simpleName ?: "unknown"),
                            "result" to "failed",
                        ),
                ),
            )
            throw e
        }
    }

    /**
     * 수신자 지정 방식 해석 — 고유코드(우선) 또는 번호 직접 입력(병행).
     * - 코드: 대문자 정규화 → User 조회. 미존재/본인/전화번호 없음 검증.
     * - 번호: name+phone 직접 사용(기존 방식).
     */
    @Suppress("ThrowsCount")
    private fun resolveShareTarget(
        requesterUserId: Long,
        receiverName: String?,
        receiverPhone: String?,
        receiverCode: String?,
    ): ShareTarget {
        val code = receiverCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
        if (code != null) {
            val receiver =
                userRepository.findByShareCode(code)
                    ?: throw ContractException.ShareCodeNotFound(code)
            if (receiver.id == requesterUserId) {
                throw ContractException.ShareToSelf(code)
            }
            if (receiver.status != UserStatus.ACTIVE) {
                throw ContractException.ShareTargetInvalid("상대방이 활성 상태가 아닙니다 (shareCode=$code)")
            }
            // 코드 공유는 수신자를 party 로 직등록하므로 번호가 없어도 성립(알림톡만 스킵).
            // 번호 미보유 계정(테스트 등) 지원. 번호 직접입력 방식은 아래에서 여전히 phone 필수.
            val phone = receiver.phone?.takeIf { it.isNotBlank() }
            return ShareTarget(name = receiver.name ?: "", phone = phone, recipientUserId = receiver.id)
        }
        val name = receiverName?.trim()?.takeIf { it.isNotEmpty() }
        val phone = receiverPhone?.trim()?.takeIf { it.isNotEmpty() }
        if (name == null || phone == null) {
            throw ContractException.ShareTargetInvalid("고유코드 또는 (이름+전화번호)가 필요합니다")
        }
        return ShareTarget(name = name, phone = phone, recipientUserId = null)
    }

    /**
     * 코드 공유 시 수신자를 계약 party 로 직등록 (초대 수락 없이 바로 서명 가능).
     * 멱등 — 이미 party 면 skip. creator 역할의 반대편으로 등록.
     */
    private fun addRecipientPartyIfAbsent(
        contract: Contract,
        recipientUserId: Long,
    ) {
        val contractId = contract.id!!
        if (contractPartyRepository.findFirstByContractIdAndUserId(contractId, recipientUserId) != null) {
            return
        }
        val creatorParty =
            contractPartyRepository.findFirstByContractIdAndUserId(contractId, contract.creatorUserId)
                ?: error("creator party 없음 — 데이터 무결성 위반 (contractId=$contractId)")
        val receiverPartyType =
            when (creatorParty.partyType) {
                PartyType.SELLER -> PartyType.BUYER
                PartyType.BUYER -> PartyType.SELLER
            }
        val party =
            ContractParty.create(
                contractId = contractId,
                userId = recipientUserId,
                partyType = receiverPartyType,
            )
        party.markValidated()
        contractPartyRepository.save(party)
    }

    /**
     * 수신자(SELLER) 가 SHARED 단계에서 보증기간 변경 — PDF v1' 즉시 재생성 (양측 일치).
     * 트랜잭션 분리 (transitionToReady 와 동일 패턴) — preview readOnly tx → render/upload (no tx) → commit rw tx.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun updateReceiverWarranty(
        publicCode: String,
        userId: Long,
        days: Int,
    ): Contract {
        val preview = committer.loadReceiverWarrantyPreview(publicCode, userId, days)
        val pdfBytes = pdfRenderer.render(preview)
        val pdfSha256 = sha256Hex(pdfBytes)
        val pdfS3Key = buildPdfS3Key(publicCode)
        pdfArchiveStorage.uploadPdf(pdfS3Key, pdfBytes)
        return committer.commitReceiverWarranty(publicCode, userId, days, pdfS3Key, pdfSha256)
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

        // EVT-045 contract_change_requested — 수정 요청 저장+상태변경 성공
        analyticsTracker.track(
            AnalyticsEvent(
                name = AnalyticsEvents.CONTRACT_CHANGE_REQUESTED,
                userId = requesterUserId,
                properties =
                    mapOf(
                        "contract_id" to contract.publicCode,
                        "actor_role" to "counterparty",
                        "contract_status_before" to from.name,
                    ),
            ),
        )

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

    /**
     * 생성자가 REVISION_REQUESTED 단계에서 본문 수정 완료 → SHARED 직접 전이 (DRAFT 경유 X).
     * - PATCH `/v1/contracts/{publicCode}` 로 본문 수정 후 호출
     * - PDF v1' 재생성 + S3 덮어쓰기 (S3 Versioning 으로 옛 v1 보존) + version += 1
     * - 새 invitation token 발급 + 기존 1번 템플릿 (sendNewContract) 재사용 — 수신자에게 "다시 확인" 알림톡
     * - 수신자는 새 token URL 클릭 → acceptInvitation (idempotent, 이미 party 면 그대로)
     * 트랜잭션 분리 (transitionToReady 동일 패턴).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun reshare(
        publicCode: String,
        userId: Long,
    ): Contract {
        val preview = committer.loadReshareReadyPreview(publicCode, userId)
        val pdfBytes = pdfRenderer.render(preview)
        val pdfSha256 = sha256Hex(pdfBytes)
        val pdfS3Key = buildPdfS3Key(publicCode)
        pdfArchiveStorage.uploadPdf(pdfS3Key, pdfBytes)
        val result = committer.commitReshare(publicCode, userId, pdfS3Key, pdfSha256)
        contractAlimtalkDispatcher.sendNewContract(result.contract, userId, result.invitation)

        // EVT-050 contract_revision_submitted + EVT-051 contract_revision_shared — 수정본 새 버전 생성·재전송 성공
        val revisionProps =
            mapOf(
                "contract_id" to result.contract.publicCode,
                "revision_number" to result.contract.version,
                "share_method" to "code",
                "actor_role" to "creator",
            )
        analyticsTracker.track(AnalyticsEvent(AnalyticsEvents.CONTRACT_REVISION_SUBMITTED, userId, revisionProps))
        analyticsTracker.track(AnalyticsEvent(AnalyticsEvents.CONTRACT_REVISION_SHARED, userId, revisionProps))
        return result.contract
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

/** 공유 수신자 해석 결과 — 이름·번호 + (코드 공유 시) 수신자 userId. phone null = 번호 미보유(알림톡 스킵). */
private data class ShareTarget(
    val name: String,
    val phone: String?,
    val recipientUserId: Long?,
)
