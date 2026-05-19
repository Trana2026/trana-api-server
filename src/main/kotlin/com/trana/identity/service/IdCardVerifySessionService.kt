package com.trana.identity.service

import com.trana.identity.entity.IdCardVerifySession
import com.trana.identity.repository.IdCardVerifySessionRepository
import org.springframework.security.crypto.encrypt.BytesEncryptor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * 신분증 OCR → Verify 사이 임시 세션 lifecycle 관리.
 *
 * - 평문 식별번호는 BytesEncryptor로 암호화 후 BYTEA 저장
 * - TTL 10분 (만료 후 cleanup task가 삭제)
 * - 도메인 service(IdentityService)는 이 service만 의존 — Repository 직접 접근 X
 */
@Service
class IdCardVerifySessionService(
    private val repository: IdCardVerifySessionRepository,
    private val bytesEncryptor: BytesEncryptor,
) {
    @Transactional
    @Suppress("LongParameterList")
    fun create(
        requestId: String,
        idType: String,
        name: String,
        personalNumber: String? = null,
        licenseNumber: String? = null,
        licenseSecurityCode: String? = null,
        passportNumber: String? = null,
        birthDate: LocalDate? = null,
        serialNumber: String? = null,
        issueDate: LocalDate? = null,
        expireDate: LocalDate? = null,
        idCardS3Key: String,
        idCardMime: String,
    ): IdCardVerifySession {
        val session =
            IdCardVerifySession(
                requestId = requestId,
                idType = idType,
                name = name,
                personalNumberEncrypted = personalNumber?.let { encrypt(it) },
                licenseNumber = licenseNumber,
                licenseSecurityCode = licenseSecurityCode,
                passportNumberEncrypted = passportNumber?.let { encrypt(it) },
                birthDate = birthDate,
                serialNumber = serialNumber,
                issueDate = issueDate,
                expireDate = expireDate,
                idCardS3Key = idCardS3Key,
                idCardMime = idCardMime,
                expiresAt = Instant.now().plus(TTL),
            )
        return repository.save(session)
    }

    /**
     * 진행 중 세션 조회. 없음 / 만료는 호출자(IdentityService)가 IdentityException 변환.
     */
    @Transactional(readOnly = true)
    fun findActive(requestId: String): IdCardVerifySession? {
        val session = repository.findById(requestId).orElse(null) ?: return null
        return if (session.isExpired()) null else session
    }

    fun decryptPersonalNumber(session: IdCardVerifySession): String? =
        session.personalNumberEncrypted?.let { decrypt(it) }

    fun decryptPassportNumber(session: IdCardVerifySession): String? =
        session.passportNumberEncrypted?.let { decrypt(it) }

    @Transactional
    fun delete(requestId: String) {
        repository.deleteById(requestId)
    }

    private fun encrypt(plain: String): ByteArray = bytesEncryptor.encrypt(plain.toByteArray(Charsets.UTF_8))

    private fun decrypt(cipher: ByteArray): String = String(bytesEncryptor.decrypt(cipher), Charsets.UTF_8)

    companion object {
        private val TTL: Duration = Duration.ofMinutes(10)
    }
}
