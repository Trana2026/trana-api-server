package com.trana.identity.service

import com.trana.audit.AuditEvent
import com.trana.audit.AuditLogger
import com.trana.guardian.GuardianException
import com.trana.guardian.entity.Guardian
import com.trana.guardian.repository.GuardianRepository
import com.trana.guardian.service.GuardianLinkService
import com.trana.identity.IdentityException
import com.trana.identity.adapter.FaceCompareAdapter
import com.trana.identity.adapter.IdCardOcrAdapter
import com.trana.identity.adapter.ImageInput
import com.trana.identity.adapter.idType
import com.trana.identity.adapter.identifierHashRaw
import com.trana.identity.adapter.toDomainGender
import com.trana.identity.entity.IdentityVerification
import com.trana.identity.entity.VerificationPurpose
import com.trana.identity.entity.VerificationStatus
import com.trana.identity.repository.IdentityVerificationRepository
import com.trana.user.entity.Gender
import com.trana.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 보호자 KYC 흐름 (Phase 6).
 *
 * - recognizeIdCard: token + 미성년자 + NotAdult 검증 + OCR + 세션 저장
 * - verifyIdCard: token belongs 검증 + KycSessionService.verifyIdCard 위임 (중복 회피)
 * - compareFaces: Compare + guardians upsert + minor.markGuardianVerified + link.markUsed
 */
@Service
@Transactional
class KycGuardianService(
    private val idCardOcrAdapter: IdCardOcrAdapter,
    private val faceCompareAdapter: FaceCompareAdapter,
    private val sessionService: IdCardVerifySessionService,
    private val verificationRepository: IdentityVerificationRepository,
    private val stateLookup: KycStateLookup,
    private val kycSessionService: KycSessionService,
    private val ocrPersister: IdCardOcrPersister,
    private val guardianRepository: GuardianRepository,
    private val guardianLinkService: GuardianLinkService,
    private val userRepository: UserRepository,
    private val idCardImageGateway: IdCardImageGateway,
    private val auditLogger: AuditLogger,
    private val idCardMasker: IdCardMasker,
    private val purger: IdentitySessionPurger,
    private val postCompareHandler: KycPostCompareHandler,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun recognizeIdCard(
        token: String,
        image: ImageInput,
    ): RecognizeIdCardResult {
        val link = stateLookup.loadActiveGuardianLink(token)
        stateLookup.loadSubjectMinor(link.userId)

        // 같은 token의 기존 PENDING 세션 즉시 정리 (재진입 케이스 — 보호자 화면 이탈 후 다시 OCR)
        verificationRepository
            .findFirstByGuardianLinkTokenAndStatus(token, VerificationStatus.PENDING)
            ?.let { purger.purgeByRequestId(it.ncpDocumentRequestId) }

        val ocr = idCardOcrAdapter.recognizeIdCard(image)
        val identifierHash = ocr.result.identifierHashRaw
        stateLookup.requireAdult(ocr.result.birthDate, identifierHash)

        if (verificationRepository.existsByIdentifierHashAndStatus(identifierHash, VerificationStatus.SUCCESS)) {
            throw IdentityException.Duplicate(identifierHash)
        }

        val verification =
            ocrPersister.persist(ocr, image) {
                IdentityVerification.startGuardian(
                    idType = ocr.result.idType.name,
                    ncpDocumentRequestId = ocr.sensitive.requestId,
                    identifierHash = identifierHash,
                    subjectUserId = link.userId,
                    guardianLinkToken = token,
                    name = ocr.result.name,
                    birthDate = ocr.result.birthDate,
                    gender = ocr.result.gender.toDomainGender(),
                )
            }

        auditLogger.log(
            eventType = AuditEvent.GUARDIAN_IDENTITY_OCR_PASSED,
            entityType = "IDENTITY_VERIFICATION",
            entityId = verification.id,
            metadata = mapOf("subjectUserId" to link.userId, "tokenPrefix" to token.take(8)),
        )
        log.info("Guardian OCR passed: requestId={}, subjectUserId={}", ocr.sensitive.requestId, link.userId)

        return RecognizeIdCardResult(
            requestId = ocr.sensitive.requestId,
            idType = ocr.result.idType.name,
            name = ocr.result.name,
            birthDate = ocr.result.birthDate,
            gender = ocr.result.gender.toDomainGender(),
        )
    }

    fun verifyIdCard(
        requestId: String,
        token: String,
    ): VerifyIdCardResult {
        val verification = stateLookup.loadPendingVerification(requestId)
        validateBelongsToToken(verification, token)
        return kycSessionService.verifyIdCard(requestId)
    }

    @Transactional(readOnly = true)
    fun previewIdCard(
        requestId: String,
        token: String,
    ): IdCardImagePreview {
        val session = stateLookup.loadActiveSession(requestId)
        val verification = stateLookup.loadPendingVerification(requestId)
        validateBelongsToToken(verification, token)

        val image = idCardImageGateway.load(session)
        val polygons = sessionService.decodeMaskRegions(session)
        val maskedBytes = idCardMasker.apply(image.bytes, polygons)
        return IdCardImagePreview(bytes = maskedBytes, mime = "image/png")
    }

    fun compareFaces(
        requestId: String,
        token: String,
        selfieImage: ImageInput,
    ): CompareGuardianResult {
        val session = stateLookup.loadActiveSession(requestId)
        val verification = stateLookup.loadPendingVerification(requestId)
        validateBelongsToToken(verification, token)

        if (!verification.verifyPassed) throw IdentityException.VerifyRequired(requestId)

        val idCardImage = idCardImageGateway.load(session)
        val result = faceCompareAdapter.compareFaces(idCardImage, selfieImage, GUARDIAN_FACE_MATCH_THRESHOLD)

        if (!result.isMatch) {
            postCompareHandler.handleCompareFailed(
                verification = verification,
                similarity = result.similarity,
                failedEvent = AuditEvent.GUARDIAN_IDENTITY_COMPARE_FAILED,
            )
        }

        val subjectUserId = checkNotNull(verification.subjectUserId) { "GUARDIAN verification에 subjectUserId 필수" }
        val minor = stateLookup.loadSubjectMinor(subjectUserId)

        val guardian =
            upsertGuardian(
                identifierHash = verification.identifierHash,
                name = checkNotNull(verification.name) { "verification.name null" },
                birthDate = checkNotNull(verification.birthDate) { "verification.birthDate null" },
                gender = checkNotNull(verification.gender) { "verification.gender null" },
            )
        val guardianId = checkNotNull(guardian.id) { "Guardian id null after save" }

        verification.markGuardianCompareSuccess(similarity = result.similarity, boundGuardianId = guardianId)
        guardianLinkService.markUsed(token)
        minor.markGuardianVerified()
        userRepository.save(minor)

        postCompareHandler.finalizeCompareSuccess(requestId, session.idCardS3Key)

        auditLogger.log(
            eventType = AuditEvent.GUARDIAN_VERIFIED_COMPLETED,
            actorUserId = subjectUserId,
            entityType = "USER",
            entityId = subjectUserId,
            metadata =
                mapOf(
                    "guardianId" to guardianId,
                    "similarity" to result.similarity,
                    "idType" to session.idType,
                ),
        )

        return CompareGuardianResult(
            subjectUserId = subjectUserId,
            guardianId = guardianId,
            verified = true,
        )
    }

    private fun upsertGuardian(
        identifierHash: String,
        name: String,
        birthDate: LocalDate,
        gender: Gender,
    ): Guardian {
        val existing = guardianRepository.findByIdentifierHash(identifierHash)
        return existing ?: guardianRepository.save(
            Guardian(
                identifierHash = identifierHash,
                name = name,
                birthDate = birthDate,
                gender = gender,
            ),
        )
    }

    private fun validateBelongsToToken(
        verification: IdentityVerification,
        token: String,
    ) {
        check(verification.purpose == VerificationPurpose.GUARDIAN) {
            "GUARDIAN purpose가 아닌 verification (purpose=${verification.purpose})"
        }
        if (verification.guardianLinkToken != token) {
            throw GuardianException.LinkInvalid(token, "verification과 token 불일치")
        }
    }
}

private const val GUARDIAN_FACE_MATCH_THRESHOLD = 0.35

data class CompareGuardianResult(
    val subjectUserId: Long,
    val guardianId: Long,
    val verified: Boolean,
)

@Suppress("ArrayInDataClass")
data class IdCardImagePreview(
    val bytes: ByteArray,
    val mime: String,
)
