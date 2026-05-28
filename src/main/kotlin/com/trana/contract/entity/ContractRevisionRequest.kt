package com.trana.contract.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

/**
 * 수신자 수정 요청 audit (WORM — insert-only).
 *
 * - SHARED 상태에서 수신자가 PDF v1 검토 후 필드별 reason 입력 → INSERT + REVISION_REQUESTED 전이
 * - 한 계약에 여러 row 가능 (생성자 수정 → 재 SHARED → 수신자 재요청)
 * - 모든 reason nullable — 수신자가 일부 필드만 요청 가능. 단 init { } 가 최소 1개 강제 (DB CHECK 와 일치)
 */
@Entity
@Table(name = "contract_revision_requests")
class ContractRevisionRequest(
    @Column(name = "contract_id", nullable = false)
    val contractId: Long,
    @Column(name = "requester_user_id", nullable = false)
    val requesterUserId: Long,
    @Column(name = "title_reason", columnDefinition = "text")
    val titleReason: String? = null,
    @Column(name = "price_reason", columnDefinition = "text")
    val priceReason: String? = null,
    @Column(name = "condition_summary_reason", columnDefinition = "text")
    val conditionSummaryReason: String? = null,
    @Column(name = "condition_details_reason", columnDefinition = "text")
    val conditionDetailsReason: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    val requestedAt: Instant? = null

    init {
        require(
            titleReason != null ||
                priceReason != null ||
                conditionSummaryReason != null ||
                conditionDetailsReason != null,
        ) { "수정 요청 — 최소 1개 필드의 reason 필수" }
    }

    companion object {
        fun create(
            contractId: Long,
            requesterUserId: Long,
            titleReason: String? = null,
            priceReason: String? = null,
            conditionSummaryReason: String? = null,
            conditionDetailsReason: String? = null,
        ): ContractRevisionRequest =
            ContractRevisionRequest(
                contractId = contractId,
                requesterUserId = requesterUserId,
                titleReason = titleReason,
                priceReason = priceReason,
                conditionSummaryReason = conditionSummaryReason,
                conditionDetailsReason = conditionDetailsReason,
            )
    }
}
