package com.trana.trustscore.repository

import com.trana.trustscore.entity.TrustScoreEvent
import com.trana.trustscore.entity.TrustScoreEventType
import org.springframework.data.jpa.repository.JpaRepository

interface TrustScoreEventRepository : JpaRepository<TrustScoreEvent, Long> {
    /**
     * 동일 (user, eventType, contract) 이벤트 중복 차단 — listener 재발화 시 멱등.
     *
     * 사용:
     * - BOTH_SIGNED : 같은 contract 의 SIGNED 가 두 번 발화돼도 점수는 1회만 적립
     * - WARRANTY_PROVIDED : 같은 contract 에 판매자 보증 1회만 적립
     */
    fun existsByUserIdAndEventTypeAndContractId(
        userId: Long,
        eventType: TrustScoreEventType,
        contractId: Long,
    ): Boolean

    /**
     * 동일 (user, eventType, dispute) 이벤트 중복 차단.
     *
     * 사용:
     * - FRAUD_REPORT_FILED_CONFIRMED : 한 신고 사기 확인 시 신고자 1회만 +5
     * - FRAUD_REPORT_RECEIVED_CONFIRMED : 한 신고 사기 확인 시 신고 대상 1회만 -15
     */
    fun existsByUserIdAndEventTypeAndDisputeId(
        userId: Long,
        eventType: TrustScoreEventType,
        disputeId: Long,
    ): Boolean
}
