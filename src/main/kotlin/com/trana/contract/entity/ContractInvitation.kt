package com.trana.contract.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Duration
import java.time.Instant

/**
 * 수신자 초대 토큰 — 생성자 "공유하기" 시점에 발급, 카카오톡 알림톡 1번 템플릿으로 발송.
 *
 * 흐름:
 * - 생성자: markReady(READY) → "공유하기" 화면에서 receiverName/receiverPhone 입력
 * - 백엔드: invitation row INSERT + 알림톡 발송 + status SHARED 전이
 * - 수신자: 카톡 URL 클릭 → 가입 + KYC + 서명 시점에 markUsed
 *
 * 불변식:
 * - 일회용 (markUsed 후 재사용 불가)
 * - TTL 7일 default (expiresAt 검증)
 */
@Entity
@Table(name = "contract_invitations")
class ContractInvitation(
    @Column(name = "contract_id", nullable = false)
    val contractId: Long,
    @Column(name = "token", nullable = false, unique = true, length = 40)
    val token: String,
    @Column(name = "receiver_name", nullable = false, length = 50)
    val receiverName: String,
    @Column(name = "receiver_phone", nullable = false, length = 20)
    val receiverPhone: String,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(name = "used_at")
    var usedAt: Instant? = null
        protected set

    @Column(name = "accepted_by_user_id")
    var acceptedByUserId: Long? = null
        protected set

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null

    fun isExpired(now: Instant = Instant.now()): Boolean = !now.isBefore(expiresAt)

    fun isActive(now: Instant = Instant.now()): Boolean = usedAt == null && !isExpired(now)

    fun markUsed(acceptedByUserId: Long) {
        check(usedAt == null) { "이미 사용된 invitation" }
        check(!isExpired()) { "만료된 invitation (expiresAt=$expiresAt)" }
        this.usedAt = Instant.now()
        this.acceptedByUserId = acceptedByUserId
    }

    companion object {
        val DEFAULT_TTL: Duration = Duration.ofDays(7)

        fun create(
            contractId: Long,
            token: String,
            receiverName: String,
            receiverPhone: String,
            ttl: Duration = DEFAULT_TTL,
        ): ContractInvitation =
            ContractInvitation(
                contractId = contractId,
                token = token,
                receiverName = receiverName,
                receiverPhone = receiverPhone,
                expiresAt = Instant.now().plus(ttl),
            )
    }
}
