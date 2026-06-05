package com.trana.contract.adapter.kakao

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * 개발/심사 대기 단계 mock — 실제 발송 X, log 로 의도만 기록.
 *
 * 활성 조건: `alimtalk-live` profile 이 **꺼져있을 때** (기본값).
 * - local/dev/test/prod 모두 기본 Mock
 * - Live 발송 활성화: `SPRING_PROFILES_ACTIVE=...,alimtalk-live` 추가
 * - [LiveAligoAlimtalkClient] 와 `!alimtalk-live` / `alimtalk-live` 로 상호 배타
 */

@Component
@Profile("!alimtalk-live")
class MockKakaoAlimtalkClient : KakaoAlimtalkClient {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun sendNewContract(message: NewContractMessage) {
        log.info(
            "[MOCK ALIMTALK] sendNewContract → to={}({}), seller={}, contract={}, price={}, url={}",
            message.receiverName,
            maskPhone(message.receiverPhone),
            message.sellerName,
            message.contractTitle,
            message.price,
            message.invitationUrl,
        )
    }

    override fun sendReceiverSigned(message: ReceiverSignedMessage) {
        log.info(
            "[MOCK ALIMTALK] sendReceiverSigned → to={}({}), receiver={}, contract={}, price={}, reviewUrl={}",
            message.creatorName,
            maskPhone(message.creatorPhone),
            message.receiverName,
            message.contractTitle,
            message.price,
            message.reviewUrl,
        )
    }

    override fun sendRevisionRequested(message: RevisionRequestedMessage) {
        log.info(
            "[MOCK ALIMTALK] sendRevisionRequested → " +
                "to={}({}), requester={}, contract={}, price={}, reason={}, reviewUrl={}",
            message.creatorName,
            maskPhone(message.creatorPhone),
            message.requesterName,
            message.contractTitle,
            message.price,
            message.revisionReason.replace("\n", " | "),
            message.reviewUrl,
        )
    }

    override fun sendCompleted(message: ContractCompletedMessage) {
        log.info(
            "[MOCK ALIMTALK] sendCompleted → to={}({}), contract={}, price={}, completedAt={}, pdfUrl={}",
            message.recipientName,
            maskPhone(message.recipientPhone),
            message.contractTitle,
            message.price,
            message.completedAt,
            message.downloadUrl,
        )
    }

    /** 010-1234-5678 → 010-****-5678 (PII 마스킹) */
    private fun maskPhone(phone: String): String =
        if (phone.length < MIN_PHONE_LENGTH) {
            "****"
        } else {
            phone.replaceRange(MASK_START..MASK_END, "****")
        }

    companion object {
        private const val MIN_PHONE_LENGTH = 10
        private const val MASK_START = 4
        private const val MASK_END = 7
    }
}
