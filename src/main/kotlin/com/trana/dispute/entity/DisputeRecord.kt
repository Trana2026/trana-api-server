package com.trana.dispute.entity

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
 * 분쟁(신고) 기록 — Aggregate root.
 *
 * - 신고자 1명 = 한 계약에 활성(REPORTED) 신고 1개만 (V11 partial UNIQUE)
 * - 신고 가능 상태: contract.status ∈ {SIGNED, COMPLETED} (Contract entity 가 강제 — A-3)
 * - 부분 WORM: contract_id / reporter_user_id / reason / detail / reporter_ip / reported_at immutable
 *   (DB trigger + entity val 이중 방어)
 * - 상태 전이: REPORTED → CANCELLED_BY_REPORTER (신고자 본인 취소)
 */
@Entity
@Table(name = "dispute_records")
class DisputeRecord(
    @Column(name = "contract_id", nullable = false)
    val contractId: Long,
    @Column(name = "reporter_user_id", nullable = false)
    val reporterUserId: Long,
    @Column(name = "reason", nullable = false, length = REASON_MAX_LENGTH)
    val reason: String,
    @Column(name = "detail", nullable = false, columnDefinition = "text")
    val detail: String,
    @Column(name = "reporter_ip", length = IP_MAX_LENGTH)
    val reporterIp: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    var status: DisputeStatus = DisputeStatus.REPORTED
        protected set

    @CreationTimestamp
    @Column(name = "reported_at", nullable = false, updatable = false)
    var reportedAt: Instant? = null
        protected set

    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null
        protected set

    /**
     * 신고자 본인이 신고 취소.
     * REPORTED 상태에서만 가능. 취소 후 같은 계약에 재신고 가능 (partial UNIQUE 가 활성만 막음).
     */
    fun cancelByReporter() {
        check(status == DisputeStatus.REPORTED) {
            "신고 취소는 REPORTED 상태에서만 가능 (current=$status)"
        }
        this.status = DisputeStatus.CANCELLED_BY_REPORTER
        this.cancelledAt = Instant.now()
    }

    companion object {
        const val REASON_MAX_LENGTH = 100
        private const val IP_MAX_LENGTH = 45
    }
}

enum class DisputeStatus {
    /** 신고 접수됨 (활성). */
    REPORTED,

    /** 신고자 본인이 취소. */
    CANCELLED_BY_REPORTER,
}
