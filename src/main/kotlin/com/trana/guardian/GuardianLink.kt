package com.trana.guardian

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

/**
 * 보호자 KYC 매칭 토큰.
 *
 * - 미성년자가 발급 → 보호자에게 SMS/카톡으로 링크 전송
 * - TTL 3일, 1회 사용 (사용 후 usedAt 마킹, 재사용 X)
 * - 재발급 시 기존 미사용·미취소·미만료 토큰은 revokedAt으로 강제 만료
 * - 토큰 자체는 audit 가치 있어 만료 후에도 보존 (cleanup 정책은 W7~ 결정)
 */
@Entity
@Table(name = "guardian_links")
class GuardianLink(
    @Column(name = "token", nullable = false, unique = true, length = 64)
    val token: String,
    @Column(name = "minor_user_id", nullable = false)
    val minorUserId: Long,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(name = "used_at")
    var usedAt: Instant? = null
        protected set

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null
        protected set

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null

    fun isUsable(now: Instant = Instant.now()): Boolean = usedAt == null && revokedAt == null && expiresAt.isAfter(now)

    /** 보호자 KYC 완료 시점에 호출 (1회용 마킹). */
    fun markUsed() {
        check(isUsable()) { "이미 사용/만료/취소된 토큰입니다" }
        usedAt = Instant.now()
    }

    /** 재발급으로 인한 강제 만료. */
    fun revoke() {
        if (usedAt != null || revokedAt != null) return // 이미 종료된 토큰은 무시 (idempotent)
        revokedAt = Instant.now()
    }
}
