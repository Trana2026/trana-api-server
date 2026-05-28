package com.trana.contract.adapter.kakao

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 개발/심사 대기 단계 mock — 실제 발송 X, log 로 의도만 기록.
 *
 * 현재 모든 profile (local/dev/test/prod) 에서 동작 — 카카오 BSP 미준비 상태.
 *
 * 향후 BSP 심사 (1~2주) 완료 후:
 * - `LiveKakaoAlimtalkClient` 추가 (`@Profile("prod")`)
 * - 본 Mock 은 `@Profile("!prod")` 로 격리
 * - prod 진입 전 반드시 Live 구현체 wire-up 확인 (보류 항목)
 */
@Component
class MockKakaoAlimtalkClient : KakaoAlimtalkClient {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun sendNewContract(message: NewContractMessage) {
        log.info(
            "[MOCK ALIMTALK] sendNewContract → to={}({}), seller={}, contract={}, url={}",
            message.receiverName,
            maskPhone(message.receiverPhone),
            message.sellerName,
            message.contractTitle,
            message.invitationUrl,
        )
    }

    override fun sendReceiverSigned(message: ReceiverSignedMessage) {
        log.info(
            "[MOCK ALIMTALK] sendReceiverSigned → to={}({}), contract={}, reviewUrl={}",
            message.creatorName,
            maskPhone(message.creatorPhone),
            message.contractTitle,
            message.reviewUrl,
        )
    }

    override fun sendCompleted(message: ContractCompletedMessage) {
        log.info(
            "[MOCK ALIMTALK] sendCompleted → to={}({}), contract={}, pdfUrl={}",
            message.recipientName,
            maskPhone(message.recipientPhone),
            message.contractTitle,
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
