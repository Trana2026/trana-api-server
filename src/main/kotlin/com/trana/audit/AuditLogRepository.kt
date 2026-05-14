package com.trana.audit

import org.springframework.data.jpa.repository.JpaRepository

interface AuditLogRepository : JpaRepository<AuditLog, Long> {
    fun findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
        entityType: String,
        entityId: Long,
    ): List<AuditLog>

    fun findByActorUserIdOrderByCreatedAtDesc(actorUserId: Long): List<AuditLog>

    fun findByEventTypeOrderByCreatedAtDesc(eventType: String): List<AuditLog>
}
