package com.trana.identity.service

import com.trana.identity.entity.VerificationStatus
import com.trana.identity.repository.IdCardVerifySessionRepository
import com.trana.identity.repository.IdentityVerificationRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 단일 KYC 세션 정리 (S3 사진 + PENDING verification + session row).
 *
 * 사용처:
 * - IdCardVerifySessionCleanupTask: 10분 TTL 만료 row 정리
 * - KycSessionService.recognizeIdCard: 본인 같은 signupSessionId로 재진입 시 기존 PENDING 즉시 정리
 * - KycGuardianService.recognizeIdCard: 보호자 같은 token으로 재진입 시 기존 PENDING 즉시 정리
 *
 * - SUCCESS/FAILED verification은 audit 가치라 보존 (PENDING만 정리)
 * - S3 delete 실패는 swallow + warn (라이프사이클 1일 fallback)
 */
@Component
class IdentitySessionPurger(
    private val sessionRepository: IdCardVerifySessionRepository,
    private val verificationRepository: IdentityVerificationRepository,
    private val idCardImageGateway: IdCardImageGateway,
) {
    @Transactional
    fun purgeByRequestId(requestId: String) {
        val session = sessionRepository.findById(requestId).orElse(null)
        if (session == null) {
            // session 없으면 verification만 따로 정리 (드문 case — 부분 정리 잔여물)
            verificationRepository.deleteByNcpDocumentRequestIdAndStatus(
                requestId,
                VerificationStatus.PENDING,
            )
            return
        }

        idCardImageGateway.deleteSwallow(session.idCardS3Key)
        verificationRepository.deleteByNcpDocumentRequestIdAndStatus(
            requestId,
            VerificationStatus.PENDING,
        )
        sessionRepository.deleteById(requestId)
    }
}
