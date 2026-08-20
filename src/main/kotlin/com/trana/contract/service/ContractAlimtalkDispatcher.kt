package com.trana.contract.service
import com.trana.common.web.WebUrlBuilder
import com.trana.contract.adapter.kakao.ContractCompletedMessage
import com.trana.contract.adapter.kakao.ContractExpiredMessage
import com.trana.contract.adapter.kakao.ExpiryWarningMessage
import com.trana.contract.adapter.kakao.GuardianContractCompletedMessage
import com.trana.contract.adapter.kakao.KakaoAlimtalkClient
import com.trana.contract.adapter.kakao.NewContractMessage
import com.trana.contract.adapter.kakao.ReceiverSignedMessage
import com.trana.contract.adapter.kakao.RevisionRequestedMessage
import com.trana.contract.adapter.kakao.sendAlimtalkBestEffort
import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractInvitation
import com.trana.contract.entity.ContractStatus
import com.trana.contract.repository.ContractInvitationRepository
import com.trana.contract.repository.ContractPartyRepository
import com.trana.identity.entity.VerificationPurpose
import com.trana.identity.entity.VerificationStatus
import com.trana.identity.repository.IdentityVerificationRepository
import com.trana.user.entity.AgeGroup
import com.trana.user.entity.User
import com.trana.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 계약 도메인 카카오 알림톡 발송 dispatcher.
 *
 * 책임:
 * - 4개 트리거 (SHARED / RECEIVER_SIGNED / SIGNED+COMPLETED / REVISION_REQUESTED) 전이 시점 발송
 * - user 조회 (creator/receiver) + URL 생성 (webUrlBuilder)
 *
 * #102 refactor — 기존 ContractStatusService 의 4개 private helper 통째 추출.
 */
@Component
class ContractAlimtalkDispatcher(
    private val kakaoAlimtalkClient: KakaoAlimtalkClient,
    private val userRepository: UserRepository,
    private val webUrlBuilder: WebUrlBuilder,
    private val contractPartyRepository: ContractPartyRepository,
    private val identityVerificationRepository: IdentityVerificationRepository,
    private val invitationRepository: ContractInvitationRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sendNewContract(
        contract: Contract,
        sellerUserId: Long,
        invitation: ContractInvitation,
    ) {
        val seller =
            userRepository.findById(sellerUserId).orElseThrow {
                IllegalStateException("계약 작성자 user 조회 실패 (userId=$sellerUserId)")
            }
        val sellerName = seller.name ?: "Trana 사용자"
        // 번호 미보유 수신자(코드 공유)는 알림톡 발송 대상이 아님 — 계약은 party 직등록으로 이미 성립. 스킵.
        val receiverPhone = invitation.receiverPhone?.takeIf { it.isNotBlank() }
        if (receiverPhone == null) {
            log.info("[ALIMTALK] newContract 스킵 — 수신자 번호 없음 (contractId={})", contract.id)
            return
        }
        sendAlimtalkBestEffort("newContract") {
            kakaoAlimtalkClient.sendNewContract(
                NewContractMessage(
                    receiverPhone = receiverPhone,
                    receiverName = invitation.receiverName,
                    sellerName = sellerName,
                    contractTitle = contract.title ?: "(제목 없음)",
                    price = requireNotNull(contract.price) { "price 누락 (SHARED 전이 후 invariant 위반)" },
                    // 초대토큰 대신 계약 상세 딥링크 — 재진입/만료 없음 (수신자는 공유 시 party 직등록됨)
                    invitationUrl = webUrlBuilder.contractDetail(contract.publicCode),
                    invitationAppUrl = webUrlBuilder.contractDetailApp(contract.publicCode),
                ),
            )
        }
    }

    fun sendReceiverSigned(
        contract: Contract,
        receiverName: String,
    ) {
        val creator =
            userRepository.findById(contract.creatorUserId).orElseThrow {
                IllegalStateException("계약 작성자 조회 실패 (userId=${contract.creatorUserId})")
            }
        val creatorName = creator.name ?: "Trana 사용자"
        val creatorPhone = creator.phone ?: "(unknown)"
        val reviewUrl = webUrlBuilder.contractDetail(contract.publicCode)
        sendAlimtalkBestEffort("receiverSigned") {
            kakaoAlimtalkClient.sendReceiverSigned(
                ReceiverSignedMessage(
                    creatorPhone = creatorPhone,
                    creatorName = creatorName,
                    receiverName = receiverName,
                    contractTitle = contract.title ?: "(제목 없음)",
                    price = requireNotNull(contract.price) { "price 누락 (RECEIVER_SIGNED 전이 후 invariant 위반)" },
                    reviewUrl = reviewUrl,
                    reviewAppUrl = webUrlBuilder.contractDetailApp(contract.publicCode),
                ),
            )
        }
    }

    fun sendCompleted(
        contract: Contract,
        creator: User,
        receiver: User,
    ) {
        val downloadUrl = webUrlBuilder.contractDetail(contract.publicCode)
        listOf(creator, receiver).forEach { recipient ->
            val recipientName = recipient.name ?: "Trana 사용자"
            val recipientPhone = recipient.phone ?: "(unknown)"
            sendAlimtalkBestEffort("completed") {
                kakaoAlimtalkClient.sendCompleted(
                    ContractCompletedMessage(
                        recipientPhone = recipientPhone,
                        recipientName = recipientName,
                        contractTitle = contract.title ?: "(제목 없음)",
                        price = requireNotNull(contract.price) { "price 누락 (COMPLETED 전이 후 invariant 위반)" },
                        completedAt =
                            requireNotNull(
                                contract.pdfGeneratedAt,
                            ) { "pdfGeneratedAt 누락 (SIGNED 전이 후 invariant 위반)" },
                        downloadUrl = downloadUrl,
                        downloadAppUrl = webUrlBuilder.contractDetailApp(contract.publicCode),
                    ),
                )
            }
        }
    }

    /**
     * SIGNED 전이 시 미성년자 party 의 가입 보호자에게 계약 체결 통보.
     * 미성년자 없거나 보호자 phone 조회 실패 시 skip (silent).
     */
    fun sendGuardianContractCompleted(contract: Contract) {
        val creator = userRepository.findById(contract.creatorUserId).orElse(null)
        val receiverParty =
            contractPartyRepository
                .findAllByContractId(contract.id!!)
                .firstOrNull { it.userId != contract.creatorUserId }
        val receiver = receiverParty?.let { userRepository.findById(it.userId).orElse(null) }

        val minorAndCounterparty: Pair<com.trana.user.entity.User, com.trana.user.entity.User>? =
            when {
                creator?.ageGroup == AgeGroup.MINOR && receiver != null -> creator to receiver
                receiver?.ageGroup == AgeGroup.MINOR && creator != null -> receiver to creator
                else -> null
            }
        if (minorAndCounterparty == null) return

        val minor = minorAndCounterparty.first
        val guardianVerification =
            identityVerificationRepository.findFirstBySubjectUserIdAndPurposeAndStatus(
                subjectUserId = minor.id!!,
                purpose = VerificationPurpose.GUARDIAN,
                status = VerificationStatus.SUCCESS,
            )
        val guardianPhone = guardianVerification?.phone

        if (guardianPhone == null) {
            log.warn(
                "[Alimtalk] 보호자 phone 조회 실패 — minorId={} publicCode={}. 알림톡 skip",
                minor.id,
                contract.publicCode,
            )
            return
        }

        sendAlimtalkBestEffort("guardianContractCompleted") {
            kakaoAlimtalkClient.sendGuardianContractCompleted(
                GuardianContractCompletedMessage(
                    recipientPhone = guardianPhone,
                    guardianName = guardianVerification.name ?: "보호자",
                    minorName = minor.name ?: "미성년 자녀",
                    contractTitle = contract.title ?: "(제목 없음)",
                    price = contract.price ?: 0L,
                    completedAt = contract.pdfGeneratedAt ?: java.time.Instant.now(),
                ),
            )
        }
    }

    fun sendRevisionRequested(
        contract: Contract,
        requesterUserId: Long,
        deliveryTypeReason: String?,
        tradingPlatformReason: String?,
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
        val creatorName = creator.name ?: "Trana 사용자"
        val creatorPhone = creator.phone ?: "(unknown)"
        val requesterName = requester.name ?: "Trana 사용자"
        val reviewUrl = webUrlBuilder.contractDetail(contract.publicCode)
        val revisionReason =
            buildRevisionReason(
                deliveryTypeReason,
                tradingPlatformReason,
                titleReason,
                priceReason,
                conditionSummaryReason,
                conditionDetailsReason,
            )
        sendAlimtalkBestEffort("revisionRequested") {
            kakaoAlimtalkClient.sendRevisionRequested(
                RevisionRequestedMessage(
                    creatorPhone = creatorPhone,
                    creatorName = creatorName,
                    contractTitle = contract.title ?: "(제목 없음)",
                    requesterName = requesterName,
                    price = requireNotNull(contract.price) { "price 누락 (REVISION_REQUESTED 전이 후 invariant 위반)" },
                    revisionReason = revisionReason,
                    reviewUrl = reviewUrl,
                    reviewAppUrl = webUrlBuilder.contractDetailApp(contract.publicCode),
                ),
            )
        }
    }

    /** 만료 30분 전 서명 대기 측에게 경고 (UJ_9524). SHARED→수신자, RECEIVER_SIGNED→생성자. */
    fun sendExpiryWarning(contract: Contract) {
        val target =
            expiryWaitingSigner(contract) ?: run {
                log.warn("[EXPIRY] 경고 대상 연락처 미상 — publicCode={} skip", contract.publicCode)
                return
            }
        sendAlimtalkBestEffort("expiryWarning") {
            kakaoAlimtalkClient.sendExpiryWarning(
                ExpiryWarningMessage(
                    recipientPhone = target.second,
                    recipientName = target.first,
                    contractTitle = contract.title ?: "(제목 없음)",
                    price = contract.price ?: 0L,
                    homeUrl = webUrlBuilder.home(),
                    homeAppUrl = webUrlBuilder.homeApp(),
                ),
            )
        }
    }

    /** 만료(삭제) 시 요청자(생성자)·미서명자(수신자)에게 각각 통보 (UJ_9527/UJ_9529). */
    fun sendExpiryDeleted(contract: Contract) {
        val creator = userRepository.findById(contract.creatorUserId).orElse(null)
        if (creator?.name != null && creator.phone != null) {
            sendAlimtalkBestEffort("expiryDeletedRequester") {
                kakaoAlimtalkClient.sendExpiryDeletedToRequester(
                    ContractExpiredMessage(
                        recipientPhone = creator.phone!!,
                        recipientName = creator.name!!,
                        contractTitle = contract.title ?: "(제목 없음)",
                        price = contract.price ?: 0L,
                    ),
                )
            }
        }
        val invitation = invitationRepository.findFirstByContractIdOrderByIdDesc(contract.id!!)
        val invitationPhone = invitation?.receiverPhone?.takeIf { it.isNotBlank() }
        if (invitation != null && invitationPhone != null) {
            sendAlimtalkBestEffort("expiryDeletedUnsigned") {
                kakaoAlimtalkClient.sendExpiryDeletedToUnsigned(
                    ContractExpiredMessage(
                        recipientPhone = invitationPhone,
                        recipientName = invitation.receiverName,
                        contractTitle = contract.title ?: "(제목 없음)",
                        price = contract.price ?: 0L,
                    ),
                )
            }
        }
    }

    /** 만료 경고 대상(미서명 측) 이름·번호. SHARED→수신자(invitation), RECEIVER_SIGNED→생성자. */
    private fun expiryWaitingSigner(contract: Contract): Pair<String, String>? =
        when (contract.status) {
            ContractStatus.SHARED ->
                invitationRepository
                    .findFirstByContractIdOrderByIdDesc(contract.id!!)
                    ?.let { inv -> inv.receiverPhone?.takeIf { it.isNotBlank() }?.let { inv.receiverName to it } }
            ContractStatus.RECEIVER_SIGNED -> {
                val creator = userRepository.findById(contract.creatorUserId).orElse(null)
                if (creator?.name != null && creator.phone != null) creator.name!! to creator.phone!! else null
            }
            else -> null
        }

    private fun buildRevisionReason(
        deliveryTypeReason: String?,
        tradingPlatformReason: String?,
        titleReason: String?,
        priceReason: String?,
        conditionSummaryReason: String?,
        conditionDetailsReason: String?,
    ): String =
        buildList {
            deliveryTypeReason?.takeIf { it.isNotBlank() }?.let { add("거래 방식: $it") }
            tradingPlatformReason?.takeIf { it.isNotBlank() }?.let { add("플랫폼: $it") }
            titleReason?.takeIf { it.isNotBlank() }?.let { add("물품명: $it") }
            priceReason?.takeIf { it.isNotBlank() }?.let { add("가격: $it") }
            conditionSummaryReason?.takeIf { it.isNotBlank() }?.let { add("상태: $it") }
            conditionDetailsReason?.takeIf { it.isNotBlank() }?.let { add("상세설명: $it") }
        }.joinToString("\n").ifBlank { "(사유 없음)" }
}
