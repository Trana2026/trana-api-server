package com.trana.contract.service

import com.trana.common.web.WebUrlBuilder
import com.trana.contract.adapter.kakao.ContractCompletedMessage
import com.trana.contract.adapter.kakao.KakaoAlimtalkClient
import com.trana.contract.adapter.kakao.NewContractMessage
import com.trana.contract.adapter.kakao.ReceiverSignedMessage
import com.trana.contract.adapter.kakao.RevisionRequestedMessage
import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractInvitation
import com.trana.user.entity.User
import com.trana.user.repository.UserRepository
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
) {
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
        val downloadUrl = webUrlBuilder.contractPdf(contract.publicCode)
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
