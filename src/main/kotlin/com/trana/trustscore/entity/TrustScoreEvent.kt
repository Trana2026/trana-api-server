package com.trana.trustscore.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

/**
 * 신뢰 점수 변동 이력 — WORM 영구 보존 (감사·분쟁 증거).
 *
 * SOT — User.trustScore / *_count 는 캐시. 이 테이블이 진실.
 * 점수 변동 발생 시 항상 row INSERT + User counter 증가 + User.applyTrustScoreDelta() 호출.
 *
 * 트리거:
 * - [TrustScoreEventType.BOTH_SIGNED] : Contract SIGNED 전이 시 양측 +2
 * - [TrustScoreEventType.WARRANTY_PROVIDED] : 판매자 보증 제공 + SIGNED 시 +3 (판매자만)
 * - [TrustScoreEventType.FRAUD_REPORT_FILED_CONFIRMED] : 신고함 + 사기 확인 +5
 * - [TrustScoreEventType.FRAUD_REPORT_RECEIVED_CONFIRMED] : 신고당함 + 사기 확인 -15
 * - [TrustScoreEventType.MIN_FLOOR] : 0점 도달 audit (delta=0, after=0)
 */
@Entity
@Table(name = "trust_score_events")
class TrustScoreEvent(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 48)
    val eventType: TrustScoreEventType,
    @Column(nullable = false)
    val delta: Int,
    @Column(name = "before_score", nullable = false)
    val beforeScore: Int,
    @Column(name = "after_score", nullable = false)
    val afterScore: Int,
    @Column(columnDefinition = "TEXT")
    val reason: String? = null,
    @Column(name = "contract_id")
    val contractId: Long? = null,
    @Column(name = "dispute_id")
    val disputeId: Long? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
        protected set
}

/**
 * 신뢰 점수 변동 이벤트 타입 — V10 chk_trust_score_events_event_type 와 일치.
 */
enum class TrustScoreEventType {
    /** 양측 서명 완료 (SIGNED) — 양측 +2 */
    BOTH_SIGNED,

    /** 판매자 보증 제공 + SIGNED — 판매자만 +3 */
    WARRANTY_PROVIDED,

    /** 신고 제기 → 운영팀 사기 확인 — 신고자 +5 */
    FRAUD_REPORT_FILED_CONFIRMED,

    /** 신고 당함 → 운영팀 사기 확인 — 신고 대상 -15 */
    FRAUD_REPORT_RECEIVED_CONFIRMED,

    /** 0점 floor 도달 audit (delta=0, after=0) */
    MIN_FLOOR,
}
