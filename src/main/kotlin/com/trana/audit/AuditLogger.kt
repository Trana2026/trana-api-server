package com.trana.audit

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 감사 로그 기록 API.
 *
 * 사용처: 인증/계약/KYC 등 도메인 서비스에서 핵심 액션 발생 시 호출.
 * 예: auditLogger.log(eventType = "USER_SIGNED_IN", actorUserId = 123L)
 */
@Service
class AuditLogger(
    private val repository: AuditLogRepository,
) {
    /**
     * 감사 로그 기록.
     *
     * - 본 트랜잭션 실패해도 audit는 남도록 [Propagation.REQUIRES_NEW] 사용 검토 가능
     *   (현재는 REQUIRED — 본 작업과 함께 commit/rollback. W7에서 정책 재검토)
     */
    @Transactional
    fun log(
        eventType: String,
        actorUserId: Long? = null,
        entityType: String? = null,
        entityId: Long? = null,
        metadata: Map<String, Any?>? = null,
        ip: String? = null,
    ) {
        repository.save(
            AuditLog(
                eventType = eventType,
                actorUserId = actorUserId,
                entityType = entityType,
                entityId = entityId,
                metadata = metadata,
                ip = ip,
            ),
        )
    }
}
