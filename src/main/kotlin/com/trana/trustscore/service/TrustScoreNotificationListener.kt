package com.trana.trustscore.service

import com.trana.notification.service.NotificationDispatchService
import com.trana.trustscore.entity.TrustScoreEventType
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 신뢰 점수 변동 시 FCM 알림 발송 listener.
 *
 * @TransactionalEventListener(AFTER_COMMIT) — 점수 적용 트랜잭션 commit 후 알림.
 * FCM 호출 실패해도 점수 이미 적용 완료 (안전성 ↑).
 * cf. TrustScoreSignedListener / DisputeListener 는 @EventListener synchronous (점수 적용 자체).
 *
 * 5 + 1 알림:
 * - eventType 별 5 메시지 (양측서명 / 보증 / 신고사기확인 / 신고당함사기확인 / 0점도달)
 * - 등급 변경 시 추가 1 알림 (상승 vs 하락 메시지 분기)
 *
 * 명세 2.5.1 상대방 알림 ("이번 거래 상대방의 신뢰 점수가 0점이에요") 은 contract 거래 진입 시점에
 * RiskSignals 로 노출 — 점수 변동 즉시 상대방에게 push 발송은 X (Phase 6 — 상대방 점수 노출 시점에 처리).
 */
@Component
class TrustScoreNotificationListener(
    private val notificationDispatchService: NotificationDispatchService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: TrustScoreChangedEvent) {
        sendDeltaNotification(event)
        if (event.beforeGrade != event.afterGrade) {
            sendGradeChangedNotification(event)
        }
    }

    private fun sendDeltaNotification(event: TrustScoreChangedEvent) {
        val (title, body) =
            when (event.eventType) {
                TrustScoreEventType.BOTH_SIGNED -> {
                    NOTIF_TITLE_SCORE_UP to "양측 서명이 완료돼 신뢰 점수가 +2점 올랐어요!"
                }

                TrustScoreEventType.WARRANTY_PROVIDED -> {
                    NOTIF_TITLE_SCORE_UP to "보증 제공으로 신뢰 점수가 +3점 올랐어요!"
                }

                TrustScoreEventType.FRAUD_REPORT_FILED_CONFIRMED -> {
                    NOTIF_TITLE_SCORE_UP to "신고가 사기로 확인됐어요. 신뢰 점수가 5점 올랐어요."
                }

                TrustScoreEventType.FRAUD_REPORT_RECEIVED_CONFIRMED -> {
                    NOTIF_TITLE_SCORE_DOWN to "신고 검토 결과 신뢰 점수가 15점 차감됐어요."
                }

                TrustScoreEventType.MIN_FLOOR -> {
                    NOTIF_TITLE_MIN_FLOOR to "신뢰 점수가 0점에 도달했어요. 거래 상대방에게 주의 거래자로 표시됩니다."
                }
            }
        notificationDispatchService.sendToUser(
            userId = event.userId,
            title = title,
            body = body,
            data =
                mapOf(
                    "type" to "TRUST_SCORE_CHANGED",
                    "eventType" to event.eventType.name,
                    "delta" to event.delta.toString(),
                    "afterScore" to event.afterScore.toString(),
                ),
        )
    }

    private fun sendGradeChangedNotification(event: TrustScoreChangedEvent) {
        val isPromote = event.afterGrade.ordinal > event.beforeGrade.ordinal
        val (title, body) =
            if (isPromote) {
                NOTIF_TITLE_GRADE_UP to "등급이 올랐어요! (${event.beforeGrade.label} → ${event.afterGrade.label})"
            } else {
                NOTIF_TITLE_GRADE_DOWN to
                    "신뢰 점수가 낮아져 등급이 변경됐어요. (${event.beforeGrade.label} → ${event.afterGrade.label})"
            }
        notificationDispatchService.sendToUser(
            userId = event.userId,
            title = title,
            body = body,
            data =
                mapOf(
                    "type" to "TRUST_GRADE_CHANGED",
                    "beforeGrade" to event.beforeGrade.name,
                    "afterGrade" to event.afterGrade.name,
                ),
        )
    }

    companion object {
        private const val NOTIF_TITLE_SCORE_UP = "신뢰 점수 적립"
        private const val NOTIF_TITLE_SCORE_DOWN = "신뢰 점수 차감"
        private const val NOTIF_TITLE_MIN_FLOOR = "신뢰 점수 최솟값 도달"
        private const val NOTIF_TITLE_GRADE_UP = "등급 상승"
        private const val NOTIF_TITLE_GRADE_DOWN = "등급 하락"
    }
}
