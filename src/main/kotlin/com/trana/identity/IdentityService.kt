package com.trana.identity

import com.trana.audit.AuditLogger
import com.trana.common.crypto.Sha256Hasher
import com.trana.common.storage.StorageService
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
import com.trana.identity.adapter.Gender as KycGender
import com.trana.user.Gender as UserGender

@Service
class IdentityService(
    private val idCardOcrAdapter: IdCardOcrAdapter,
    private val idCardVerifyAdapter: IdCardVerifyAdapter,
    private val faceCompareAdapter: FaceCompareAdapter,
    private val sessionService: IdCardVerifySessionService,
    private val verificationRepository: IdentityVerificationRepository,
    private val userService: UserService,
    private val auditLogger: AuditLogger,
    private val storageService: StorageService,
) {
    @Transactional
    fun recognizeIdCard(
        image: ImageInput,
        userId: Long? = null,
    ): IdCardOcrOutput {
        val output = idCardOcrAdapter.recognizeIdCard(image)
        val s3Key = "identity/${output.sensitive.requestId}/id-card.${image.format.extension}"
        storageService.put(s3Key, image.bytes, image.format.mime)
        sessionService.save(output.sensitive.toSessionData(output.result.idType, s3Key, image.format.mime))
        val record = verificationRepository.save(output.toPendingVerification(userId))
        auditLogger.log(
            eventType = EVENT_OCR,
            actorUserId = userId,
            entityType = ENTITY_TYPE,
            entityId = record.id,
            metadata =
                mapOf(
                    "idType" to output.result.idType.name,
                    "ncpRequestId" to output.sensitive.requestId,
                    "s3Key" to s3Key,
                ),
        )
        return output
    }

    @Transactional
    fun verifyIdCard(requestId: String): IdCardVerifyResult {
        val session = sessionService.findActive(requestId)
        val result = idCardVerifyAdapter.verify(session.toVerifyInput())
        val record = updateVerificationRecord(requestId) { it.applyVerify(result) }
        auditLogger.log(
            eventType = EVENT_VERIFY,
            actorUserId = record.userId,
            entityType = ENTITY_TYPE,
            entityId = record.id,
            metadata =
                mapOf(
                    "passed" to result.isValid,
                    "errorCode" to result.errorCode,
                    "errorMessage" to result.errorMessage,
                ),
        )
        return result
    }

    @Transactional
    fun compareFaces(
        requestId: String,
        selfieImage: ImageInput,
    ): FaceCompareResult {
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
            actorUserId = record.userId,
            entityType = ENTITY_TYPE,
            entityId = record.id,
            metadata =
                mapOf(
                    "similarity" to result.similarity,
                    "isMatch" to result.isMatch,
                    "finalStatus" to record.status.name,
                ),
        )
        if (record.status == IdentityVerificationStatus.SUCCESS && record.userId != null) {
            backfillUser(record)
        }
        return result
    }

    private fun backfillUser(record: IdentityVerification) {
        val userId = record.userId ?: return
        val birthDate = record.birthDate ?: error("KYC birthDate null")
        val gender = record.gender ?: error("KYC gender null")
        val user = userService.getById(userId)
        user.applyKycResult(
            name = record.name ?: error("KYC name null"),
            birthDate = birthDate,
            gender = gender.toUserGender(),
            ageGroup = computeAgeGroup(birthDate),
        )
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

    private fun IdCardOcrOutput.toPendingVerification(userId: Long?): IdentityVerification =
        IdentityVerification(
            userId = userId,
            idType = result.idType,
            ncpDocumentRequestId = sensitive.requestId,
            identifierHash = result.computeIdentifierHash(),
            name = result.name,
            birthDate = result.birthDate,
            gender = result.gender,
        )

    private fun IdCardRecognitionResult.computeIdentifierHash(): String =
        when (this) {
            is IdCardRecognitionResult.ResidentIdCard -> personalNumberHash
            is IdCardRecognitionResult.DriverLicense -> personalNumberHash
            is IdCardRecognitionResult.Passport -> Sha256Hasher.hashHex(passportNumber)
            is IdCardRecognitionResult.AlienRegistration -> alienRegNumberHash
        }

    private fun KycGender.toUserGender(): UserGender =
        when (this) {
            KycGender.MALE -> UserGender.MALE
            KycGender.FEMALE -> UserGender.FEMALE
        }

    private fun computeAgeGroup(birthDate: LocalDate): AgeGroup =
        if (Period.between(birthDate, LocalDate.now()).years >= ADULT_AGE) {
            AgeGroup.ADULT
        } else {
            AgeGroup.MINOR
        }

    private fun IdCardSensitiveData.toSessionData(
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
}

private const val SESSION_TTL_MINUTES = 10L
private const val ADULT_AGE = 19 // 한국 민법상 만 19세 성인
private const val EVENT_OCR = "IDENTITY_OCR"
private const val EVENT_VERIFY = "IDENTITY_VERIFY"
private const val EVENT_COMPARE = "IDENTITY_COMPARE"
private const val ENTITY_TYPE = "IDENTITY_VERIFICATION"
