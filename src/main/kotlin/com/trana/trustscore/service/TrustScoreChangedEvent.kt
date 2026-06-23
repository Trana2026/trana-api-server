package com.trana.trustscore.service

import com.trana.trustscore.entity.TrustScoreEventType
import com.trana.user.entity.TrustGrade

/**
 * 신뢰 점수 변동 발생 이벤트.
 *
 * TrustScoreService 의 5 apply 메서드 (각 trust_score_events INSERT 마지막) 가 publish.
 *
 * Listener:
 * - TrustScoreNotificationListener (@TransactionalEventListener AFTER_COMMIT) — FCM 알림 + 등급 변경 알림
 *
 * 패턴 — @EventListener (synchronous, same tx) 대신 AFTER_COMMIT:
 * - FCM 호출 실패해도 점수는 이미 commit 됨 (안전성 ↑)
 * - FCM = 외부 I/O, 트랜잭션 안에 두면 DB 커넥션 점유
 *
 * payload 에 before/after grade 둘 다 포함 — listener 가 등급 변경 감지 (User 재조회 불필요).
 */
data class TrustScoreChangedEvent(
    val userId: Long,
    val eventType: TrustScoreEventType,
    val delta: Int,
    val beforeScore: Int,
    val afterScore: Int,
    val beforeGrade: TrustGrade,
    val afterGrade: TrustGrade,
)
