package com.trana.identity.service

import com.trana.audit.AuditEvent
import com.trana.audit.AuditLogger
import com.trana.identity.IdentityException
import com.trana.identity.entity.IdentityVerification
import org.springframework.stereotype.Component

/**
 * Compare 후처리 공통 helper (refactor q).
 *
 * - handleCompareFailed: markCompareFailed + audit + throw (성인/보호자 공통)
 * - finalizeCompareSuccess: session row + S3 객체 정리 (성인/보호자 공통)
 *
 * 이전: KycSignupService.compareFaces + KycGuardianService.compareFaces 가
 *      같은 실패 분기 / 같은 정리 두 줄을 평행으로 보유 → 정리 누락 / audit 누락 위험
 * 정리: 실패 / 정리 한 곳에서만 변경 — eventType 만 호출자가 주입
 */
@Component
class KycPostCompareHandler(
    private val auditLogger: AuditLogger,
    private val sessionService: IdCardVerifySessionService,
    private val idCardImageGateway: IdCardImageGateway,
) {
    /** Compare 실패 처리 — markCompareFailed + audit + throw CompareRejected. */
    fun handleCompareFailed(
        verification: IdentityVerification,
        similarity: Double,
        failedEvent: AuditEvent,
    ): Nothing {
        verification.markCompareFailed(
            similarity = similarity,
            errorCode = "FACE_MISMATCH",
            errorMessage = "얼굴 유사도 임계값 미달 (similarity=$similarity)",
        )
        auditLogger.log(
            eventType = failedEvent,
            entityType = "IDENTITY_VERIFICATION",
            entityId = verification.id,
            metadata = mapOf("similarity" to similarity),
        )
        throw IdentityException.CompareRejected(similarity = similarity)
    }

    /** Compare 성공 후 정리 — session row + S3 객체 삭제 (순서: session row 먼저). */
    fun finalizeCompareSuccess(
        requestId: String,
        s3Key: String?,
    ) {
        sessionService.delete(requestId)
        idCardImageGateway.deleteSwallow(s3Key)
    }
}
