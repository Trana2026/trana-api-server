package com.trana.contract.service

import com.trana.common.web.WebUrlBuilder
import com.trana.contract.adapter.kakao.ContractCompletedMessage
import com.trana.contract.adapter.kakao.GuardianContractCompletedMessage
import com.trana.contract.adapter.kakao.KakaoAlimtalkClient
import com.trana.contract.adapter.kakao.NewContractMessage
import com.trana.contract.adapter.kakao.ReceiverSignedMessage
import com.trana.contract.adapter.kakao.RevisionRequestedMessage
import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractInvitation
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

    fun sendCompleted(
        contract: Contract,
        creator: User,
        receiver: User,
    ) {
        val downloadUrl = webUrlBuilder.contractDetail(contract.publicCode)
        listOf(creator, receiver).forEach { recipient ->
            val recipientName = recipient.name ?: "Trana 사용자"
            val recipientPhone = recipient.phone ?: "(unknown)"
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
                ),
            )
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

        val (minor, counterparty) = minorAndCounterparty
        val guardianPhone =
            identityVerificationRepository
                .findFirstBySubjectUserIdAndPurposeAndStatus(
                    subjectUserId = minor.id!!,
                    purpose = VerificationPurpose.GUARDIAN,
                    status = VerificationStatus.SUCCESS,
                )?.phone

        if (guardianPhone == null) {
            log.warn(
                "[Alimtalk] 보호자 phone 조회 실패 — minorId={} publicCode={}. 알림톡 skip",
                minor.id,
                contract.publicCode,
            )
            return
        }

        kakaoAlimtalkClient.sendGuardianContractCompleted(
            GuardianContractCompletedMessage(
                recipientPhone = guardianPhone,
                minorName = minor.name ?: "미성년 자녀",
                counterpartyName = counterparty.name ?: "거래 상대방",
                contractTitle = contract.title ?: "(제목 없음)",
                price = requireNotNull(contract.price) { "price 누락 (SIGNED 전이 후 invariant)" },
                contractDetailUrl = webUrlBuilder.contractDetail(contract.publicCode),
            ),
        )
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
        deliveryTypeReason: String?,
        tradingPlatformReason: String?,
        titleReason: String?,
        priceReason: String?,
        conditionSummaryReason: String?,
        conditionDetailsReason: String?,
    ): String =
        buildList {
            deliveryTypeReason?.let { add("거래 방식: $it") }
            tradingPlatformReason?.let { add("플랫폼: $it") }
            titleReason?.let { add("물품명: $it") }
            priceReason?.let { add("가격: $it") }
            conditionSummaryReason?.let { add("상태: $it") }
            conditionDetailsReason?.let { add("상세설명: $it") }
        }.joinToString("\n").ifBlank { "(사유 없음)" }
}
