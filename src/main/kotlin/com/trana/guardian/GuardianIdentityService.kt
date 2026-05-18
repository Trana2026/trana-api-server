package com.trana.guardian

import com.trana.audit.AuditLogger
import com.trana.common.crypto.Sha256Hasher
import com.trana.common.storage.StorageService
import com.trana.common.util.PublicCodeGenerator
import com.trana.identity.IdCardSessionData
import com.trana.identity.IdCardVerifySessionService
import com.trana.identity.IdentityPurpose
import com.trana.identity.IdentityVerification
import com.trana.identity.IdentityVerificationRepository
import com.trana.identity.IdentityVerificationStatus
import com.trana.identity.adapter.FaceCompareAdapter
import com.trana.identity.adapter.FaceCompareResult
import com.trana.identity.adapter.IdCardOcrAdapter
import com.trana.identity.adapter.IdCardOcrOutput
import com.trana.identity.adapter.IdCardRecognitionResult
import com.trana.identity.adapter.IdCardSensitiveData
import com.trana.identity.adapter.IdCardVerifyAdapter
import com.trana.identity.adapter.IdCardVerifyInput
import com.trana.identity.adapter.IdCardVerifyResult
import com.trana.identity.adapter.IdType
import com.trana.identity.adapter.ImageFormat
import com.trana.identity.adapter.ImageInput
import com.trana.identity.adapter.idType
import com.trana.user.AgeGroup
import com.trana.user.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.Period

/**
 * 보호자 KYC 도메인 서비스.
 *
 * - 인증: JWT X, GuardianLink 토큰 매번 검증
 * - identity_verifications: purpose=GUARDIAN, subject_user_id=minor_user_id, guardian_link_token=token
 * - 본인 KYC와 어댑터/sessionService/storageService/verificationRepository 공유
 */
@Service
class GuardianIdentityService(
    private val idCardOcrAdapter: IdCardOcrAdapter,
    private val idCardVerifyAdapter: IdCardVerifyAdapter,
    private val faceCompareAdapter: FaceCompareAdapter,
    private val sessionService: IdCardVerifySessionService,
    private val storageService: StorageService,
    private val verificationRepository: IdentityVerificationRepository,
    private val guardianLinkService: GuardianLinkService,
    private val guardianRepository: GuardianRepository,
    private val guardianRelationRepository: GuardianRelationRepository,
    private val userService: UserService,
    private val publicCodeGenerator: PublicCodeGenerator,
    private val auditLogger: AuditLogger,
) {
    @Transactional
    fun recognizeIdCard(
        image: ImageInput,
        token: String,
    ): IdCardOcrOutput {
        val link = guardianLinkService.findValidLink(token)
        val output = idCardOcrAdapter.recognizeIdCard(image)
        if (computeAgeGroup(output.result.birthDate) != AgeGroup.ADULT) {
            throw GuardianException.NotAdult(output.result.birthDate)
        }
        val s3Key = "identity-guardian/${output.sensitive.requestId}/id-card.${image.format.extension}"
        storageService.put(s3Key, image.bytes, image.format.mime)
        sessionService.save(output.sensitive.toGuardianSessionData(output.result.idType, s3Key, image.format.mime))
        val record =
            verificationRepository.save(
                output.toPendingGuardianVerification(token = token, minorUserId = link.minorUserId),
            )
        auditLogger.log(
            eventType = EVENT_OCR,
            actorUserId = null,
            entityType = ENTITY_TYPE,
            entityId = record.id,
            metadata =
                mapOf(
                    "purpose" to IdentityPurpose.GUARDIAN.name,
                    "idType" to output.result.idType.name,
                    "ncpRequestId" to output.sensitive.requestId,
                    "guardianLinkToken" to token,
                    "minorUserId" to link.minorUserId,
                    "s3Key" to s3Key,
                ),
        )
        return output
    }

    @Transactional
    fun verifyIdCard(
        requestId: String,
        token: String,
    ): IdCardVerifyResult {
        guardianLinkService.findValidLink(token)
        val session = sessionService.findActive(requestId)
        val result = idCardVerifyAdapter.verify(session.toVerifyInput())
        val record = updateVerificationRecord(requestId) { it.applyVerify(result) }
        auditLogger.log(
            eventType = EVENT_VERIFY,
            actorUserId = null,
            entityType = ENTITY_TYPE,
            entityId = record.id,
            metadata =
                mapOf(
                    "purpose" to IdentityPurpose.GUARDIAN.name,
                    "passed" to result.isValid,
                    "errorCode" to result.errorCode,
                    "errorMessage" to result.errorMessage,
                    "guardianLinkToken" to token,
                ),
        )
        return result
    }

    private fun IdCardOcrOutput.toPendingGuardianVerification(
        token: String,
        minorUserId: Long,
    ): IdentityVerification =
        IdentityVerification(
            userId = null,
            idType = result.idType,
            ncpDocumentRequestId = sensitive.requestId,
            identifierHash = result.computeIdentifierHash(),
            name = result.name,
            birthDate = result.birthDate,
            gender = result.gender,
            purpose = IdentityPurpose.GUARDIAN,
            subjectUserId = minorUserId,
            guardianLinkToken = token,
        )

    private fun IdCardRecognitionResult.computeIdentifierHash(): String =
        when (this) {
            is IdCardRecognitionResult.ResidentIdCard -> personalNumberHash
            is IdCardRecognitionResult.DriverLicense -> personalNumberHash
            is IdCardRecognitionResult.Passport -> Sha256Hasher.hashHex(passportNumber)
            is IdCardRecognitionResult.AlienRegistration -> alienRegNumberHash
        }

    private fun updateVerificationRecord(
        requestId: String,
        block: (IdentityVerification) -> Unit,
    ): IdentityVerification {
        val record =
            verificationRepository.findByNcpDocumentRequestId(requestId)
                ?: error("IdentityVerification record 없음 (requestId=$requestId)")
        block(record)
        return verificationRepository.save(record)
    }

    private fun IdCardSessionData.toVerifyInput(): IdCardVerifyInput {
        val base =
            IdCardVerifyInput(
                requestId = requestId,
                idType = idType,
                name = name,
                issueDate = issueDate,
            )
        return when (idType) {
            IdType.ID_CARD -> {
                base.copy(personalNum = personalNumber)
            }

            IdType.DRIVER_LICENSE -> {
                base.copy(
                    personalNum = personalNumber,
                    licenseNum = licenseNumber,
                    licenseCode = licenseSecurityCode,
                )
            }

            IdType.PASSPORT -> {
                base.copy(
                    passportNum = passportNumber,
                    birthDate = birthDate,
                    expireDate = expireDate,
                )
            }

            IdType.ALIEN_REGISTRATION -> {
                base.copy(
                    alienRegNum = personalNumber,
                    serialNum = serialNumber,
                )
            }
        }
    }

    private fun IdCardSensitiveData.toGuardianSessionData(
        idType: IdType,
        s3Key: String,
        mime: String,
    ): IdCardSessionData =
        IdCardSessionData(
            requestId = requestId,
            idType = idType,
            name = name,
            personalNumber = personalNumber,
            licenseNumber = licenseNumber,
            licenseSecurityCode = licenseSecurityCode,
            passportNumber = passportNumber,
            birthDate = birthDate,
            serialNumber = serialNumber,
            issueDate = issueDate,
            expireDate = expireDate,
            idCardS3Key = s3Key,
            idCardMime = mime,
            expiresAt = OffsetDateTime.now().plusMinutes(SESSION_TTL_MINUTES),
        )

    @Transactional
    fun compareFaces(
        requestId: String,
        selfieImage: ImageInput,
        token: String,
    ): FaceCompareResult {
        guardianLinkService.findValidLink(token)
        val session = sessionService.findActive(requestId)
        val s3Key = session.idCardS3Key ?: error("세션에 idCardS3Key 없음 (requestId=$requestId)")
        val mime = session.idCardMime ?: error("세션에 idCardMime 없음 (requestId=$requestId)")
        val format = ImageFormat.fromMime(mime)
        val idCardImage =
            ImageInput(
                bytes = storageService.get(s3Key),
                format = format,
                originalFilename = "id-card.${format.extension}",
            )
        val result =
            try {
                faceCompareAdapter.compareFaces(idCardImage, selfieImage)
            } finally {
                runCatching { storageService.delete(s3Key) }
            }
        val record = updateVerificationRecord(requestId) { it.applyCompare(result) }
        auditLogger.log(
            eventType = EVENT_COMPARE,
            actorUserId = null,
            entityType = ENTITY_TYPE,
            entityId = record.id,
            metadata =
                mapOf(
                    "purpose" to IdentityPurpose.GUARDIAN.name,
                    "similarity" to result.similarity,
                    "isMatch" to result.isMatch,
                    "finalStatus" to record.status.name,
                    "guardianLinkToken" to token,
                ),
        )
        if (record.status == IdentityVerificationStatus.SUCCESS) {
            handleGuardianSuccess(record, token)
        }
        return result
    }

    private fun handleGuardianSuccess(
        record: IdentityVerification,
        token: String,
    ) {
        val minorUserId = record.subjectUserId ?: error("GUARDIAN record에 subjectUserId 없음")
        val guardian =
            guardianRepository.findByIdentifierHash(record.identifierHash)
                ?: guardianRepository.save(
                    Guardian(
                        publicCode = publicCodeGenerator.generate(),
                        identifierHash = record.identifierHash,
                    ),
                )
        val guardianId = checkNotNull(guardian.id) { "Guardian id null after save" }
        record.linkToGuardian(guardianId)

        val existingRelation =
            guardianRelationRepository.findByGuardianIdAndMinorUserIdAndStatus(
                guardianId,
                minorUserId,
                GuardianRelationStatus.ACTIVE,
            )
        if (existingRelation == null) {
            guardianRelationRepository.save(
                GuardianRelation(guardianId = guardianId, minorUserId = minorUserId),
            )
        }

        userService.getById(minorUserId).markGuardianVerified()
        guardianLinkService.markUsed(token)

        auditLogger.log(
            eventType = EVENT_GUARDIAN_VERIFIED,
            actorUserId = null,
            entityType = ENTITY_USER,
            entityId = minorUserId,
            metadata =
                mapOf(
                    "guardianId" to guardianId,
                    "guardianPublicCode" to guardian.publicCode,
                    "guardianLinkToken" to token,
                    "identityVerificationId" to record.id,
                ),
        )
    }

    private fun computeAgeGroup(birthDate: LocalDate): AgeGroup =
        if (Period.between(birthDate, LocalDate.now()).years >= ADULT_AGE) {
            AgeGroup.ADULT
        } else {
            AgeGroup.MINOR
        }
}

private const val SESSION_TTL_MINUTES = 10L
private const val EVENT_OCR = "IDENTITY_OCR"
private const val EVENT_VERIFY = "IDENTITY_VERIFY"
private const val ENTITY_TYPE = "IDENTITY_VERIFICATION"
private const val ADULT_AGE = 19
private const val EVENT_COMPARE = "IDENTITY_COMPARE"
private const val EVENT_GUARDIAN_VERIFIED = "GUARDIAN_VERIFIED"
private const val ENTITY_USER = "USER"
