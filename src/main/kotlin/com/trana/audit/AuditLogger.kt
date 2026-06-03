package com.trana.audit

import org.slf4j.MDC
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
        eventType: AuditEvent, // ← String → AuditEvent
        actorUserId: Long? = null,
        entityType: String? = null,
        entityId: Long? = null,
        metadata: Map<String, Any?>? = null,
        ip: String? = null,
        userAgent: String? = null,
    ) {
        // MDC 자동 fallback (refactor mm) — 호출자 명시 안 하면 RequestMdcFilter 가 채운 컨텍스트 사용
        val resolvedIp = ip ?: MDC.get(MDC_IP)
        val resolvedUserAgent = userAgent ?: MDC.get(MDC_USER_AGENT)

        repository.save(
            AuditLog(
                eventType = eventType.name, // ← enum → DB String
                actorUserId = actorUserId,
                entityType = entityType,
                entityId = entityId,
                metadata = metadata,
                ip = ip,
            ),
        )
    }

    /** AuditLog 에 userAgent 컬럼 없으므로 metadata 에 함께 저장. */
    private fun mergeUserAgent(
        metadata: Map<String, Any?>?,
        userAgent: String?,
    ): Map<String, Any?>? {
        if (userAgent.isNullOrBlank()) return metadata
        val base = metadata ?: emptyMap()
        return base + ("userAgent" to userAgent)
    }

    companion object {
        private const val MDC_IP = "ip"
        private const val MDC_USER_AGENT = "userAgent"
    }
}
