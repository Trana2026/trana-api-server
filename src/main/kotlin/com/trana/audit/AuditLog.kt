package com.trana.audit

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnTransformer
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.Immutable
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

/**
 * 감사 로그 — WORM (Write Once Read Many).
 *
 * DB 레벨 WORM 트리거 + Hibernate @Immutable + (향후) DB role 권한 = 3중 방어.
 * setter 없음. 한 번 저장하면 절대 수정/삭제 불가.
 */
@Entity
@Immutable
@Table(name = "audit_logs")
class AuditLog(
    @Column(name = "event_type", nullable = false, length = 50)
    val eventType: String,
    @Column(name = "actor_user_id")
    val actorUserId: Long? = null,
    @Column(name = "entity_type", length = 50)
    val entityType: String? = null,
    @Column(name = "entity_id")
    val entityId: Long? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    val metadata: Map<String, Any?>? = null,
    @ColumnTransformer(write = "?::inet")
    @Column(name = "ip", columnDefinition = "inet")
    val ip: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime? = null
}
