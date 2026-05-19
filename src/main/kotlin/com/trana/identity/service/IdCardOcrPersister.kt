package com.trana.identity.service

import com.trana.common.storage.StorageService
import com.trana.identity.adapter.IdCardOcrOutput
import com.trana.identity.adapter.ImageInput
import com.trana.identity.adapter.idType
import com.trana.identity.entity.IdentityVerification
import com.trana.identity.repository.IdentityVerificationRepository
import org.springframework.stereotype.Component

/**
 * OCR 통과 후 영구 저장 파이프라인 — SIGNUP/GUARDIAN 공통.
 *
 * - S3에 신분증 사진 업로드
 * - id_card_verify_sessions에 평문 식별번호 + 메타 저장 (10분 TTL, BytesEncryptor)
 * - identity_verifications에 PENDING record 저장 (factory로 SIGNUP/GUARDIAN 분기)
 *
 * caller (KycSessionService / KycGuardianService)는 도메인별 검증/audit만 책임.
 */
@Component
class IdCardOcrPersister(
    private val sessionService: IdCardVerifySessionService,
    private val verificationRepository: IdentityVerificationRepository,
    private val storageService: StorageService,
) {
    fun persist(
        ocr: IdCardOcrOutput,
        image: ImageInput,
        verificationFactory: () -> IdentityVerification,
    ): IdentityVerification {
        val s3Key = "identity/${ocr.sensitive.requestId}/id-card.${image.format.extension}"
        storageService.put(s3Key, image.bytes, image.format.mime)

        sessionService.create(
            requestId = ocr.sensitive.requestId,
            idType = ocr.result.idType.name,
            name = ocr.sensitive.name,
            personalNumber = ocr.sensitive.personalNumber,
            licenseNumber = ocr.sensitive.licenseNumber,
            licenseSecurityCode = ocr.sensitive.licenseSecurityCode,
            passportNumber = ocr.sensitive.passportNumber,
            birthDate = ocr.sensitive.birthDate,
            serialNumber = ocr.sensitive.serialNumber,
            issueDate = ocr.sensitive.issueDate,
            expireDate = ocr.sensitive.expireDate,
            idCardS3Key = s3Key,
            idCardMime = image.format.mime,
        )

        return verificationRepository.save(verificationFactory())
    }
}
