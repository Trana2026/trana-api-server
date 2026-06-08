package com.trana.contract.entity

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
 * 계약 취소 요청 (W7 Phase A') — Aggregate root.
 *
 * - 한 계약 활성(REQUESTED) 취소요청 1건만 (V12 partial UNIQUE)
 * - 요청 가능 시점: contract.status ∈ {SHARED, RECEIVER_SIGNED} (Contract entity 가 강제)
 * - 부분 WORM: contract_id / requester_user_id / reason / detail / requester_ip / requested_at immutable
 *   (DB trigger + entity val 이중 방어)
 * - 상태 전이: REQUESTED → CONFIRMED (상대 측 확정 시)
 */
@Entity
@Table(name = "contract_cancellation_requests")
class ContractCancellationRequest(
    @Column(name = "contract_id", nullable = false)
    val contractId: Long,
    @Column(name = "requester_user_id", nullable = false)
    val requesterUserId: Long,
    @Column(name = "reason", nullable = false, length = REASON_MAX_LENGTH)
    val reason: String,
    @Column(name = "detail", nullable = false, columnDefinition = "text")
    val detail: String,
    @Column(name = "requester_ip", length = IP_MAX_LENGTH)
    val requesterIp: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: CancellationStatus = CancellationStatus.REQUESTED
        protected set

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    var requestedAt: Instant? = null
        protected set

    @Column(name = "confirmed_at")
    var confirmedAt: Instant? = null
        protected set

    @Column(name = "confirmed_by_user_id")
    var confirmedByUserId: Long? = null
        protected set

    /**
     * 상대 측이 취소 확정 — REQUESTED → CONFIRMED.
     * service 가 confirmer != requester 검증 후 호출.
     */
    fun confirmByCounterparty(confirmerUserId: Long) {
        check(status == CancellationStatus.REQUESTED) {
            "확정은 REQUESTED 상태에서만 가능 (current=$status)"
        }
        check(confirmerUserId != requesterUserId) {
            "취소 요청자 본인은 자기 요청 확정 불가 — service 가 사전 검증"
        }
        this.status = CancellationStatus.CONFIRMED
        this.confirmedAt = Instant.now()
        this.confirmedByUserId = confirmerUserId
    }

    companion object {
        const val REASON_MAX_LENGTH = 100
        private const val IP_MAX_LENGTH = 45
    }
}

enum class CancellationStatus {
    /** 요청 접수됨 (활성). */
    REQUESTED,

    /** 상대 측이 확정 — 계약 자체도 CANCELLED 전이. */
    CONFIRMED,
}
