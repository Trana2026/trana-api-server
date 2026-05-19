package com.trana.identity.service

import com.trana.audit.AuditLogger
import com.trana.common.security.JwtProvider
import com.trana.common.storage.StorageService
import com.trana.identity.IdentityException
import com.trana.identity.adapter.FaceCompareAdapter
import com.trana.identity.adapter.ImageFormat
import com.trana.identity.adapter.ImageInput
import com.trana.identity.entity.IdCardVerifySession
import com.trana.identity.entity.IdentityVerification
import com.trana.identity.repository.IdentityVerificationRepository
import com.trana.terms.service.ConsentService
import com.trana.user.entity.AgeGroup
import com.trana.user.service.UserService
import org.slf4j.LoggerFactory
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
    private val verificationRepository: IdentityVerificationRepository,
    private val stateLookup: KycStateLookup,
    private val consentService: ConsentService,
    private val userService: UserService,
    private val storageService: StorageService,
    private val jwtProvider: JwtProvider,
    private val auditLogger: AuditLogger,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun compareFaces(
        requestId: String,
        selfieImage: ImageInput,
    ): CompareFacesResult {
        val session = stateLookup.loadActiveSession(requestId)
        val verification = stateLookup.loadPendingVerification(requestId)
        val phone = ensureReadyForCompare(verification, requestId)

        val idCardImage = loadIdCardImage(session)
        val result = faceCompareAdapter.compareFaces(idCardImage, selfieImage)

        if (!result.isMatch) {
            verification.markCompareFailed(
                similarity = result.similarity,
                errorCode = "FACE_MISMATCH",
                errorMessage = "얼굴 유사도 임계값 미달 (similarity=${result.similarity})",
            )
            auditLogger.log(
                eventType = "IDENTITY_COMPARE_FAILED",
                entityType = "IDENTITY_VERIFICATION",
                entityId = verification.id,
                metadata = mapOf("similarity" to result.similarity),
            )
            throw IdentityException.CompareRejected(similarity = result.similarity)
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
        sessionService.delete(requestId)
        deleteIdCardImage(session.idCardS3Key)

        val accessToken = jwtProvider.createAccessToken(newUserId)
        val refreshToken = jwtProvider.createRefreshToken(newUserId)

        auditLogger.log(
            eventType = "IDENTITY_SIGNUP_COMPLETED",
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

    private fun ensureReadyForCompare(
        verification: IdentityVerification,
        requestId: String,
    ): String {
        if (!verification.verifyPassed || verification.phone.isNullOrBlank()) {
            throw IdentityException.VerifyRequired(requestId)
        }
        return verification.phone!!
    }

    private fun loadIdCardImage(session: IdCardVerifySession): ImageInput {
        val s3Key = checkNotNull(session.idCardS3Key) { "session.idCardS3Key null" }
        val mime = checkNotNull(session.idCardMime) { "session.idCardMime null" }
        val format = ImageFormat.fromMime(mime)
        val bytes = storageService.get(s3Key)
        return ImageInput(bytes = bytes, format = format, originalFilename = "id-card.${format.extension}")
    }

    private fun deleteIdCardImage(s3Key: String?) {
        if (s3Key == null) return
        runCatching { storageService.delete(s3Key) }
            .onFailure { log.warn("S3 id-card delete failed (lifecycle 1d will cleanup): key={}", s3Key, it) }
    }
}

data class CompareFacesResult(
    val accessToken: String,
    val refreshToken: String,
    val publicCode: String,
    val requiresGuardian: Boolean,
)
