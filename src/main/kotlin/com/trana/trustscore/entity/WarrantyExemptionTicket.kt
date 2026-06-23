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
 * 계약 생성 수수료 면제 티켓.
 *
 * 발급 (매월 1일 00:00 KST batch):
 * - 신뢰 등급 (55~74) — 월 1장 (GRADE_TRUST_MONTHLY)
 * - 우수 등급 (75~89) — 월 3장 (GRADE_EXCELLENT_MONTHLY)
 * - 최우수 등급 (90~100) — row 발급 X, 등급 자체로 결제 0원 분기 (canExempt 검사)
 *
 * 유효기간 = 발급일 +30일. 사용 안 하면 EXPIRED 마킹 (점수/등급 영향 X).
 * 만료 3일 전 FCM 알림 1회 (expiry_notified_at 으로 중복 차단).
 *
 * 사용:
 * - 1회용 (status UNUSED → USED 단방향)
 * - used_at + used_contract_id 채움 + status=USED
 */
@Entity
@Table(name = "warranty_exemption_tickets")
class WarrantyExemptionTicket(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "issue_reason", nullable = false, length = 48)
    val issueReason: IssueReason,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: TicketStatus = TicketStatus.UNUSED
        protected set

    @CreationTimestamp
    @Column(name = "issued_at", nullable = false, updatable = false)
    var issuedAt: Instant? = null
        protected set

    @Column(name = "used_at")
    var usedAt: Instant? = null
        protected set

    @Column(name = "used_contract_id")
    var usedContractId: Long? = null
        protected set

    @Column(name = "expiry_notified_at")
    var expiryNotifiedAt: Instant? = null
        protected set

    /** 사용 — UNUSED → USED 단방향. */
    fun markUsed(contractId: Long) {
        check(status == TicketStatus.UNUSED) { "사용 불가 상태 (status=$status)" }
        this.status = TicketStatus.USED
        this.usedAt = Instant.now()
        this.usedContractId = contractId
    }

    /** 만료 — UNUSED → EXPIRED (batch cleanup). */
    fun markExpired() {
        check(status == TicketStatus.UNUSED) { "만료 불가 상태 (status=$status)" }
        this.status = TicketStatus.EXPIRED
    }

    /** 만료 3일 전 알림 발송 시각 기록 (중복 알림 차단). */
    fun markExpiryNotified() {
        check(expiryNotifiedAt == null) { "이미 만료 임박 알림 발송됨" }
        this.expiryNotifiedAt = Instant.now()
    }
}

enum class TicketStatus { UNUSED, USED, EXPIRED }

/**
 * 티켓 발급 사유 — V10 chk_warranty_exemption_tickets_issue_reason 와 일치.
 */
enum class IssueReason {
    /** 신뢰 등급 (55~74) 월 1회 발급. */
    GRADE_TRUST_MONTHLY,

    /** 우수 등급 (75~89) 월 3회 발급. */
    GRADE_EXCELLENT_MONTHLY,
}
