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
 * 계약 상태 전이 WORM 로그.
 *
 * - insert-only (audit / 분쟁 증거 5년 보존)
 * - INITIAL 전이는 fromStatus = null
 * - actorUserId = null 이면 시스템 자동 전이 (예: 양측 서명 완료 → SIGNED 자동 전이)
 *
 * 불변식:
 * - 모든 필드 val (insert 후 수정 X)
 * - Repository 에 delete 메서드 의도적 미노출 (app-level WORM)
 */
@Entity
@Table(name = "contract_status_logs")
class ContractStatusLog(
    @Column(name = "contract_id", nullable = false)
    val contractId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    val fromStatus: ContractStatus?,
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    val toStatus: ContractStatus,
    @Column(name = "actor_user_id")
    val actorUserId: Long? = null,
    @Column(name = "reason", columnDefinition = "text")
    val reason: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    val changedAt: Instant? = null

    companion object {
        fun create(
            contractId: Long,
            fromStatus: ContractStatus?,
            toStatus: ContractStatus,
            actorUserId: Long?,
            reason: String?,
        ): ContractStatusLog =
            ContractStatusLog(
                contractId = contractId,
                fromStatus = fromStatus,
                toStatus = toStatus,
                actorUserId = actorUserId,
                reason = reason,
            )
    }
}
