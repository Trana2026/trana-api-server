package com.trana.audit

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 감사 로그 기록 API.
 *
 * 사용처: 인증/계약/KYC 등 도메인 서비스에서 핵심 액션 발생 시 호출.
 * 예: auditLogger.log(eventType = "USER_SIGNED_IN", actorUserId = 123L)
 *
 * 트랜잭션 정책 — REQUIRES_NEW (refactor jj):
 * - 본 비즈니스 메서드 트랜잭션이 rollback 되어도 audit row 는 별도 트랜잭션으로 commit
 * - WORM (insert-only) 의 핵심 의미 보장 — 실패 사실 자체가 audit 에서 사라지지 않음
 * - audit DB 다운 시 caller 도 fail (best-effort 가 아닌 strict 정책) — audit/도메인 같은 DB 가정.
 *   향후 audit DB 분리 시점에 try-catch + best-effort 패턴 재검토
 */
@Service
class AuditLogger(
    private val repository: AuditLogRepository,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
