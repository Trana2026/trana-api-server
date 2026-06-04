package com.trana.identity.service

import com.trana.audit.AuditEvent
import com.trana.audit.AuditLogger
import com.trana.common.security.JwtProvider
import com.trana.identity.IdentityException
import com.trana.identity.IdentityFaceMatchProperties
import com.trana.identity.adapter.FaceCompareAdapter
import com.trana.identity.adapter.ImageInput
import com.trana.identity.entity.IdentityVerification
import com.trana.identity.entity.VerificationStatus
import com.trana.identity.repository.IdentityVerificationRepository
import com.trana.terms.service.ConsentService
import com.trana.user.entity.AgeGroup
import com.trana.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * KYC 가입 완료 단계 (성인).
 *
 * Step 4. compareFaces — 셀카 vs 신분증 얼굴 비교 → user 생성 + JWT 발급
 *
 * KYC 데이터 수집 단계(recognize/verify/recordPhone)는 KycSessionService 책임.
 */
@Service
@Transactional
class KycSignupService(
    private val faceCompareAdapter: FaceCompareAdapter,
    private val sessionService: IdCardVerifySessionService,
    private val stateLookup: KycStateLookup,
    private val consentService: ConsentService,
    private val userService: UserService,
    private val idCardImageGateway: IdCardImageGateway,
    private val jwtProvider: JwtProvider,
    private val auditLogger: AuditLogger,
    private val verificationRepository: IdentityVerificationRepository,
    private val postCompareHandler: KycPostCompareHandler,
    private val identityFaceMatchProperties: IdentityFaceMatchProperties,
) {
    fun compareFaces(
        requestId: String,
        selfieImage: ImageInput,
    ): CompareFacesResult {
        val session = stateLookup.loadActiveSession(requestId)
        val verification = stateLookup.loadPendingVerification(requestId)
        val phone = ensureReadyForCompare(verification, requestId)

        val idCardImage = idCardImageGateway.load(session)
        val result =
            faceCompareAdapter.compareFaces(idCardImage, selfieImage, identityFaceMatchProperties.adultThreshold)

        if (!result.isMatch) {
            postCompareHandler.handleCompareFailed(
                verification = verification,
                similarity = result.similarity,
                failedEvent = AuditEvent.IDENTITY_COMPARE_FAILED,
            )
        }

        val signupSessionId =
            checkNotNull(verification.signupSessionId) { "SIGNUP verification은 signupSessionId 필수" }

        val newUser =
            userService.createFromKyc(
                name = checkNotNull(verification.name) { "verification.name null" },
                birthDate = checkNotNull(verification.birthDate) { "verification.birthDate null" },
                gender = checkNotNull(verification.gender) { "verification.gender null" },
                phone = phone,
                ageGroup = AgeGroup.ADULT,
            )
        val newUserId = checkNotNull(newUser.id) { "User id null after save" }

        verification.markCompareSuccess(similarity = result.similarity, boundUserId = newUserId)
        consentService.backfillUserId(signupSessionId, newUserId)
        postCompareHandler.finalizeCompareSuccess(requestId, session.idCardS3Key)

        val accessToken = jwtProvider.createAccessToken(newUserId)
        val refreshToken = jwtProvider.createRefreshToken(newUserId)

        auditLogger.log(
            eventType = AuditEvent.IDENTITY_SIGNUP_COMPLETED,
            actorUserId = newUserId,
            entityType = "USER",
            entityId = newUserId,
            metadata = mapOf("similarity" to result.similarity, "idType" to session.idType),
        )

        return CompareFacesResult(
            accessToken = accessToken,
            refreshToken = refreshToken,
            publicCode = newUser.publicCode,
            requiresGuardian = false,
        )
    }

    /**
     * 사용자 명시적 이탈 (DELETE /v1/identity/session).
     *
     * - idempotent: 없는 세션 요청도 정상 처리 (audit 만 다르게 기록)
     * - 삭제 순서: S3 → identity_verifications IN_PROGRESS → id_card_verify_session → audit
     * - SUCCESS/FAILED verification은 audit 가치라 보존
     */
    fun cancelSession(requestId: String) {
        val session = sessionService.findActive(requestId)
        if (session == null) {
            auditLogger.log(
                eventType = AuditEvent.SIGNUP_KYC_CANCEL_NOOP,
                entityType = "ID_CARD_VERIFY_SESSION",
                metadata = mapOf("requestIdPrefix" to requestId.take(8)),
            )
            return
        }

        idCardImageGateway.deleteSwallow(session.idCardS3Key)

        val verification = verificationRepository.findByNcpDocumentRequestId(requestId)
        if (verification != null && verification.status == VerificationStatus.PENDING) {
            verificationRepository.delete(verification)
        }

        sessionService.delete(requestId)

        auditLogger.log(
            eventType = AuditEvent.SIGNUP_KYC_CANCELED,
            entityType = "ID_CARD_VERIFY_SESSION",
            metadata =
                mapOf(
                    "requestIdPrefix" to requestId.take(8),
                    "hadS3Object" to (session.idCardS3Key != null),
                    "hadPendingVerification" to (verification?.status == VerificationStatus.PENDING),
                ),
        )
    }

    private fun ensureReadyForCompare(
        verification: IdentityVerification,
        requestId: String,
    ): String {
        if (!verification.verifyPassed || verification.phone.isNullOrBlank()) {
            throw IdentityException.VerifyRequired(requestId)
        }
        return verification.phone!!
    }
}

data class CompareFacesResult(
    val accessToken: String,
    val refreshToken: String,
    val publicCode: String,
    val requiresGuardian: Boolean,
)
