package com.trana.guardian.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

/**
 * 미성년자→보호자 일회용 토큰 (jnanoid 21자, TTL 3일).
 *
 * 흐름:
 * - 미성년자가 POST /v1/guardian/links → token 발급 (TTL 3일)
 * - 보호자가 trana-web에서 토큰 링크 접근 → Phase 6 보호자 KYC 호출
 * - Compare SUCCESS 시 markUsed() — 재사용 차단
 */
@Entity
@Table(name = "guardian_links")
class GuardianLink(
    @Id
    @Column(name = "token", length = 64)
    val token: String,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
) {
    @Column(name = "used_at")
    var usedAt: Instant? = null
        protected set

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null

    fun isExpired(now: Instant = Instant.now()): Boolean = !now.isBefore(expiresAt)

    fun isActive(now: Instant = Instant.now()): Boolean = usedAt == null && !isExpired(now)

    fun markUsed() {
        check(usedAt == null) { "이미 사용된 토큰" }
        check(!isExpired()) { "만료된 토큰" }
        usedAt = Instant.now()
    }
}
