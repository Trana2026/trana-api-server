package com.trana.trustscore.service

import com.trana.trustscore.dto.TrustScoreResponse
import com.trana.trustscore.entity.TrustScoreEvent
import com.trana.trustscore.entity.TrustScoreEventType
import com.trana.trustscore.repository.TrustScoreEventRepository
import com.trana.user.UserException
import com.trana.user.entity.TrustGrade
import com.trana.user.entity.User
import com.trana.user.repository.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 신뢰 점수 도메인 서비스.
 *
 * Phase 1 — 마이페이지 카드 조회만 (read-only).
 * Phase 2+ 에서 다음 추가:
 * - applyDelta(...) : 점수 변동 + trust_score_events INSERT + User counter +1
 * - SIGNED listener (ContractStatusChangedEvent)
 * - 사기 판정 listener (DisputeResolvedEvent)
 * - 등급 변경 감지 + FCM 발송 (NotificationDispatchService 위임)
 */
@Service
@Transactional(readOnly = true)
class TrustScoreService(
    private val userRepository: UserRepository,
    private val eventRepository: TrustScoreEventRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /** 본인 신뢰 점수 카드 조회 — GET /v1/users/me/trust-score */
    fun getMyTrustScore(userId: Long): TrustScoreResponse {
        val user =
            userRepository.findById(userId).orElseThrow {
                UserException.NotFound(userId.toString())
            }
        val grade = user.trustGrade
        return TrustScoreResponse(
            trustScore = user.trustScore,
            trustGrade = grade,
            trustGradeLabel = grade.label,
            completedContractCount = user.completedContractCount,
            warrantyProvidedCount = user.warrantyProvidedCount,
            fraudReportReceivedCount = user.fraudReportReceivedCount,
        )
    }

    /**
     * SIGNED 전이 시 양측 신뢰 점수 적립.
     *
     * - 양측 각자 +2 (BOTH_SIGNED) + completedContractCount ++
     * - warrantyProvided 시 seller 추가 +3 (WARRANTY_PROVIDED) + warrantyProvidedCount ++
     * - 중복 차단 — 같은 (user, eventType, contract) 이벤트 이미 있으면 skip (멱등)
     *
     * Listener 가 호출 — TrustScoreSignedListener (@EventListener for ContractStatusChangedEvent SIGNED).
     * 트랜잭션 propagation = REQUIRED — publisher 의 tx 안에서 함께 commit/rollback.
     */
    @Transactional
    fun applyBothSigned(
        sellerId: Long,
        buyerId: Long,
        contractId: Long,
        warrantyProvided: Boolean,
    ) {
        applyBothSignedForUser(sellerId, contractId)
        applyBothSignedForUser(buyerId, contractId)
        if (warrantyProvided) {
            applyWarrantyProvidedForSeller(sellerId, contractId)
        }
    }

    private fun applyBothSignedForUser(
        userId: Long,
        contractId: Long,
    ) {
        if (eventRepository.existsByUserIdAndEventTypeAndContractId(
                userId,
                TrustScoreEventType.BOTH_SIGNED,
                contractId,
            )
        ) {
            return
        }
        val user =
            userRepository.findById(userId).orElseThrow {
                UserException.NotFound(userId.toString())
            }
        val (before, after) = user.applyTrustScoreDelta(BOTH_SIGNED_DELTA)
        user.incrementCompletedContractCount()
        eventRepository.save(
            TrustScoreEvent(
                userId = userId,
                eventType = TrustScoreEventType.BOTH_SIGNED,
                delta = BOTH_SIGNED_DELTA,
                beforeScore = before,
                afterScore = after,
                contractId = contractId,
                reason = "양측 서명 완료",
            ),
        )
        publishChanged(userId, TrustScoreEventType.BOTH_SIGNED, BOTH_SIGNED_DELTA, before, after)
    }

    private fun applyWarrantyProvidedForSeller(
        sellerId: Long,
        contractId: Long,
    ) {
        if (eventRepository.existsByUserIdAndEventTypeAndContractId(
                sellerId,
                TrustScoreEventType.WARRANTY_PROVIDED,
                contractId,
            )
        ) {
            return
        }
        val seller =
            userRepository.findById(sellerId).orElseThrow {
                UserException.NotFound(sellerId.toString())
            }
        val (before, after) = seller.applyTrustScoreDelta(WARRANTY_PROVIDED_DELTA)
        seller.incrementWarrantyProvidedCount()
        eventRepository.save(
            TrustScoreEvent(
                userId = sellerId,
                eventType = TrustScoreEventType.WARRANTY_PROVIDED,
                delta = WARRANTY_PROVIDED_DELTA,
                beforeScore = before,
                afterScore = after,
                contractId = contractId,
                reason = "판매자 보증 제공",
            ),
        )
        publishChanged(sellerId, TrustScoreEventType.WARRANTY_PROVIDED, WARRANTY_PROVIDED_DELTA, before, after)
    }

    /**
     * 사기 확인 판정 — 신고자 +5.
     *
     * - 동일 (user, eventType, dispute) 멱등 차단
     * - 신고자 점수 +5 + fraudReportFiledCount ++
     */
    @Transactional
    fun applyFraudReportFiledConfirmed(
        reporterUserId: Long,
        disputeId: Long,
    ) {
        if (eventRepository.existsByUserIdAndEventTypeAndDisputeId(
                reporterUserId,
                TrustScoreEventType.FRAUD_REPORT_FILED_CONFIRMED,
                disputeId,
            )
        ) {
            return
        }
        val user =
            userRepository.findById(reporterUserId).orElseThrow {
                UserException.NotFound(reporterUserId.toString())
            }
        val (before, after) = user.applyTrustScoreDelta(FRAUD_REPORT_FILED_DELTA)
        user.incrementFraudReportFiledCount()
        eventRepository.save(
            TrustScoreEvent(
                userId = reporterUserId,
                eventType = TrustScoreEventType.FRAUD_REPORT_FILED_CONFIRMED,
                delta = FRAUD_REPORT_FILED_DELTA,
                beforeScore = before,
                afterScore = after,
                disputeId = disputeId,
                reason = "신고 사기 확인 (신고자 적립)",
            ),
        )
        publishChanged(
            reporterUserId,
            TrustScoreEventType.FRAUD_REPORT_FILED_CONFIRMED,
            FRAUD_REPORT_FILED_DELTA,
            before,
            after,
        )
    }

    /**
     * 사기 확인 판정 — 신고 대상 -15. 0점 floor 도달 시 MIN_FLOOR audit event 추가.
     *
     * - 동일 (user, eventType, dispute) 멱등 차단
     * - 신고 대상 점수 -15 (0점 floor) + fraudReportReceivedCount ++
     * - before > 0 + after == 0 시 MIN_FLOOR audit event 추가 (사용자 알림 trigger 용, Phase 4)
     */
    @Transactional
    fun applyFraudReportReceivedConfirmed(
        reportedUserId: Long,
        disputeId: Long,
    ) {
        if (eventRepository.existsByUserIdAndEventTypeAndDisputeId(
                reportedUserId,
                TrustScoreEventType.FRAUD_REPORT_RECEIVED_CONFIRMED,
                disputeId,
            )
        ) {
            return
        }
        val user =
            userRepository.findById(reportedUserId).orElseThrow {
                UserException.NotFound(reportedUserId.toString())
            }
        val (before, after) = user.applyTrustScoreDelta(FRAUD_REPORT_RECEIVED_DELTA)
        user.incrementFraudReportReceivedCount()
        eventRepository.save(
            TrustScoreEvent(
                userId = reportedUserId,
                eventType = TrustScoreEventType.FRAUD_REPORT_RECEIVED_CONFIRMED,
                delta = FRAUD_REPORT_RECEIVED_DELTA,
                beforeScore = before,
                afterScore = after,
                disputeId = disputeId,
                reason = "신고 사기 확인 (신고 대상 차감)",
            ),
        )
        publishChanged(reportedUserId, TrustScoreEventType.MIN_FLOOR, 0, User.MIN_TRUST_SCORE, User.MIN_TRUST_SCORE)
        if (before > User.MIN_TRUST_SCORE && after == User.MIN_TRUST_SCORE) {
            eventRepository.save(
                TrustScoreEvent(
                    userId = reportedUserId,
                    eventType = TrustScoreEventType.MIN_FLOOR,
                    delta = 0,
                    beforeScore = User.MIN_TRUST_SCORE,
                    afterScore = User.MIN_TRUST_SCORE,
                    disputeId = disputeId,
                    reason = "최솟값 도달",
                ),
            )
        }
    }

    private fun publishChanged(
        userId: Long,
        eventType: TrustScoreEventType,
        delta: Int,
        before: Int,
        after: Int,
    ) {
        eventPublisher.publishEvent(
            TrustScoreChangedEvent(
                userId = userId,
                eventType = eventType,
                delta = delta,
                beforeScore = before,
                afterScore = after,
                beforeGrade = TrustGrade.fromScore(before),
                afterGrade = TrustGrade.fromScore(after),
            ),
        )
    }

    companion object {
        private const val BOTH_SIGNED_DELTA = 2
        private const val WARRANTY_PROVIDED_DELTA = 3
        private const val FRAUD_REPORT_FILED_DELTA = 5
        private const val FRAUD_REPORT_RECEIVED_DELTA = -15
    }
}
