package com.trana.identity.service

import com.trana.audit.AuditLogger
import com.trana.common.storage.StorageService
import com.trana.identity.IdentityException
import com.trana.identity.adapter.IdCardOcrAdapter
import com.trana.identity.adapter.IdCardVerifyAdapter
import com.trana.identity.adapter.IdCardVerifyInput
import com.trana.identity.adapter.IdType
import com.trana.identity.adapter.ImageFormat
import com.trana.identity.adapter.ImageInput
import com.trana.identity.adapter.idType
import com.trana.identity.adapter.identifierHashRaw
import com.trana.identity.adapter.toDomainGender
import com.trana.identity.entity.IdCardVerifySession
import com.trana.identity.entity.IdentityVerification
import com.trana.identity.entity.VerificationStatus
import com.trana.identity.repository.IdentityVerificationRepository
import com.trana.user.entity.Gender
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

/**
 * KYC 데이터 수집/검증 단계 (사용자 미생성).
 *
 * Step 1. recognizeIdCard — OCR + S3 업로드 + 세션/PENDING 기록
 * Step 2. verifyIdCard    — NCP 진위확인
 * Step 3. recordPhone     — phone 저장
 *
 * 가입 완료 (compareFaces + user 생성 + JWT)는 KycSignupService 책임.
 */
@Service
@Transactional
class KycSessionService(
    private val idCardOcrAdapter: IdCardOcrAdapter,
    private val idCardVerifyAdapter: IdCardVerifyAdapter,
    private val sessionService: IdCardVerifySessionService,
    private val verificationRepository: IdentityVerificationRepository,
    private val stateLookup: KycStateLookup,
    private val ocrPersister: IdCardOcrPersister,
    private val auditLogger: AuditLogger,
    private val storageService: StorageService,
    private val idCardMasker: IdCardMasker,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun recognizeIdCard(
        signupSessionId: UUID,
        image: ImageInput,
    ): RecognizeIdCardResult {
        stateLookup.validateSignupSession(signupSessionId)

        val ocr = idCardOcrAdapter.recognizeIdCard(image)
        val identifierHash = ocr.result.identifierHashRaw

        if (verificationRepository.existsByIdentifierHashAndStatus(identifierHash, VerificationStatus.SUCCESS)) {
            throw IdentityException.Duplicate(identifierHash)
        }

        val verification =
            ocrPersister.persist(ocr, image) {
                IdentityVerification.startSignup(
                    idType = ocr.result.idType.name,
                    ncpDocumentRequestId = ocr.sensitive.requestId,
                    identifierHash = identifierHash,
                    signupSessionId = signupSessionId,
                    name = ocr.result.name,
                    birthDate = ocr.result.birthDate,
                    gender = ocr.result.gender.toDomainGender(),
                )
            }

        auditLogger.log(
            eventType = "IDENTITY_OCR_PASSED",
            entityType = "IDENTITY_VERIFICATION",
            entityId = verification.id,
            metadata = mapOf("idType" to ocr.result.idType.name, "signupSessionId" to signupSessionId.toString()),
        )
        log.info("OCR passed: requestId={}, idType={}", ocr.sensitive.requestId, ocr.result.idType)

        return RecognizeIdCardResult(
            requestId = ocr.sensitive.requestId,
            idType = ocr.result.idType.name,
            name = ocr.result.name,
            birthDate = ocr.result.birthDate,
            gender = ocr.result.gender.toDomainGender(),
        )
    }

    fun verifyIdCard(requestId: String): VerifyIdCardResult {
        val session = stateLookup.loadActiveSession(requestId)
        val verification = stateLookup.loadPendingVerification(requestId)

        val input =
            session.toVerifyInput(
                personalNum = sessionService.decryptPersonalNumber(session),
            )
        val result = idCardVerifyAdapter.verify(input)

        if (!result.isValid) {
            val errorCode = result.errorCode ?: "VERIFY_FAILED"
            val errorMessage = result.errorMessage ?: "신분증 진위확인 실패"
            verification.markVerifyFailed(errorCode = errorCode, errorMessage = errorMessage)
            auditLogger.log(
                eventType = "IDENTITY_VERIFY_FAILED",
                entityType = "IDENTITY_VERIFICATION",
                entityId = verification.id,
                metadata = mapOf("errorCode" to errorCode),
            )
            throw IdentityException.VerifyRejected(ncpErrorCode = errorCode, ncpErrorMessage = errorMessage)
        }

        verification.markVerifyPassed()
        auditLogger.log(
            eventType = "IDENTITY_VERIFY_PASSED",
            entityType = "IDENTITY_VERIFICATION",
            entityId = verification.id,
        )
        return VerifyIdCardResult(requestId = requestId, verified = true)
    }

    @Transactional(readOnly = true)
    fun previewIdCard(requestId: String): IdCardImagePreview {
        val session = stateLookup.loadActiveSession(requestId)
        stateLookup.loadPendingVerification(requestId)

        val image = loadIdCardImage(session)
        val polygons = sessionService.decodeMaskRegions(session)
        val maskedBytes = idCardMasker.apply(image.bytes, polygons)
        return IdCardImagePreview(bytes = maskedBytes, mime = "image/png")
    }

    fun recordPhone(
        requestId: String,
        phone: String,
    ): RecordPhoneResult {
        stateLookup.loadActiveSession(requestId)
        val verification = stateLookup.loadPendingVerification(requestId)

        if (!verification.verifyPassed) {
            throw IdentityException.VerifyRequired(requestId)
        }

        val digits = phone.filter { it.isDigit() }
        require(digits.length == PHONE_DIGITS && digits.startsWith("010")) {
            "휴대폰 번호 형식이 올바르지 않습니다 (010으로 시작하는 11자리)"
        }

        verification.recordPhone(digits)
        auditLogger.log(
            eventType = "IDENTITY_PHONE_RECORDED",
            entityType = "IDENTITY_VERIFICATION",
            entityId = verification.id,
        )
        return RecordPhoneResult(requestId = requestId, phone = digits)
    }

    private fun loadIdCardImage(session: IdCardVerifySession): ImageInput {
        val s3Key = checkNotNull(session.idCardS3Key) { "session.idCardS3Key null" }
        val mime = checkNotNull(session.idCardMime) { "session.idCardMime null" }
        val format = ImageFormat.fromMime(mime)
        return ImageInput(
            bytes = storageService.get(s3Key),
            format = format,
            originalFilename = "id-card.${format.extension}",
        )
    }

    companion object {
        private const val PHONE_DIGITS = 11
    }
}

// ───── Result DTOs ─────

data class RecognizeIdCardResult(
    val requestId: String,
    val idType: String,
    val name: String,
    val birthDate: LocalDate,
    val gender: Gender,
)

data class VerifyIdCardResult(
    val requestId: String,
    val verified: Boolean,
)

data class RecordPhoneResult(
    val requestId: String,
    val phone: String,
)

// ───── 파일 내부 헬퍼 ─────

private fun IdCardVerifySession.toVerifyInput(personalNum: String?): IdCardVerifyInput {
    val type = IdType.valueOf(idType)
    return when (type) {
        IdType.ID_CARD -> {
            IdCardVerifyInput(
                requestId = requestId,
                idType = type,
                name = name,
                personalNum = personalNum,
                issueDate = issueDate,
            )
        }

        IdType.DRIVER_LICENSE -> {
            IdCardVerifyInput(
                requestId = requestId,
                idType = type,
                name = name,
                personalNum = personalNum,
                licenseNum = licenseNumber,
                licenseCode = licenseSecurityCode,
                issueDate = issueDate,
            )
        }

        IdType.ALIEN_REGISTRATION -> {
            IdCardVerifyInput(
                requestId = requestId,
                idType = type,
                name = name,
                alienRegNum = personalNum,
                serialNum = serialNumber,
                issueDate = issueDate,
            )
        }
    }
}
