package com.trana.identity

import com.trana.identity.adapter.IdType
import org.springframework.security.crypto.encrypt.BytesEncryptor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 신분증 Verify 세션 관리.
 *
 * - OCR 후 평문 식별번호 저장 (AES-256-GCM 암호화)
 * - Verify 단계에서 requestId로 조회 → 평문 복호화 → NCP Verify 호출 input
 * - @Scheduled cleanup task가 deleteExpired 호출 (Step 4-3)
 */
@Service
class IdCardVerifySessionService(
    private val repository: IdCardVerifySessionRepository,
    private val bytesEncryptor: BytesEncryptor,
) {
    @Transactional
    fun save(data: IdCardSessionData) {
        val entity =
            IdCardVerifySession(
                requestId = data.requestId,
                idType = data.idType,
                name = data.name,
                personalNumberEncrypted = data.personalNumber?.encrypt(),
                licenseNumber = data.licenseNumber,
                licenseSecurityCode = data.licenseSecurityCode,
                passportNumberEncrypted = data.passportNumber?.encrypt(),
                birthDate = data.birthDate,
                serialNumber = data.serialNumber,
                issueDate = data.issueDate,
                expireDate = data.expireDate,
                idCardS3Key = data.idCardS3Key,
                idCardMime = data.idCardMime,
                expiresAt = data.expiresAt,
            )
        repository.save(entity)
    }

    @Transactional(readOnly = true)
    fun findActive(requestId: String): IdCardSessionData {
        val session =
            repository
                .findById(requestId)
                .orElseThrow { IdentityException.SessionNotFound(requestId) }
        if (session.isExpired()) {
            throw IdentityException.SessionExpired(requestId)
        }
        return session.toData()
    }

    @Transactional
    fun deleteExpired(now: OffsetDateTime = OffsetDateTime.now()): Long = repository.deleteByExpiresAtBefore(now)

    private fun IdCardVerifySession.toData(): IdCardSessionData =
        IdCardSessionData(
            requestId = requestId,
            idType = idType,
            name = name,
            personalNumber = personalNumberEncrypted?.decrypt(),
            licenseNumber = licenseNumber,
            licenseSecurityCode = licenseSecurityCode,
            passportNumber = passportNumberEncrypted?.decrypt(),
            birthDate = birthDate,
            serialNumber = serialNumber,
            issueDate = issueDate,
            expireDate = expireDate,
            idCardS3Key = idCardS3Key,
            idCardMime = idCardMime,
            expiresAt = expiresAt,
        )

    private fun String.encrypt(): ByteArray = bytesEncryptor.encrypt(toByteArray(Charsets.UTF_8))

    private fun ByteArray.decrypt(): String = String(bytesEncryptor.decrypt(this), Charsets.UTF_8)
}

@Suppress("LongParameterList")
data class IdCardSessionData(
    val requestId: String,
    val idType: IdType,
    val name: String,
    val personalNumber: String? = null,
    val licenseNumber: String? = null,
    val licenseSecurityCode: String? = null,
    val passportNumber: String? = null,
    val birthDate: LocalDate? = null,
    val serialNumber: String? = null,
    val issueDate: LocalDate? = null,
    val expireDate: LocalDate? = null,
    val idCardS3Key: String? = null,
    val idCardMime: String? = null,
    val expiresAt: OffsetDateTime,
)
