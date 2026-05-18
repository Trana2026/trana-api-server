package com.trana.guardian

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
 * 미성년자-보호자 관계 (가입 시 1회 확정).
 *
 * - 계약 시점 보호자 인증은 별도 (W4~ identity_verifications.purpose=CONTRACT_GUARDIAN)
 * - 동일 (guardian, minor) 페어는 ACTIVE 상태로 1개만 존재 (UNIQUE partial index)
 * - 해지 시 status=REVOKED + revokedAt 기록 (row 삭제 X — audit trail)
 */
@Entity
@Table(name = "guardian_relations")
class GuardianRelation(
    @Column(name = "guardian_id", nullable = false)
    val guardianId: Long,
    @Column(name = "minor_user_id", nullable = false)
    val minorUserId: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: GuardianRelationStatus = GuardianRelationStatus.ACTIVE
        protected set

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null
        protected set

    fun revoke() {
        check(status == GuardianRelationStatus.ACTIVE) { "이미 해지된 관계입니다" }
        status = GuardianRelationStatus.REVOKED
        revokedAt = Instant.now()
    }
}

enum class GuardianRelationStatus { ACTIVE, REVOKED }
