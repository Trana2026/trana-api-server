package com.trana.identity

import com.trana.identity.adapter.IdType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 신분증 OCR → Verify 단계까지의 임시 세션.
 *
 * - PK: NCP Document API의 requestId (10분 유효)
 * - 평문 식별번호는 BYTEA 암호화 (Service 단계에서 BytesEncryptor 적용)
 * - 만료 후 @Scheduled cleanup task가 삭제
 */
@Entity
@Table(name = "id_card_verify_sessions")
@Suppress("LongParameterList")
class IdCardVerifySession(
    @Id
    @Column(name = "request_id", length = 100)
    val requestId: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "id_type", nullable = false, length = 30)
    val idType: IdType,
    @Column(name = "name", nullable = false, length = 100)
    val name: String,
    @Column(name = "personal_number_encrypted")
    val personalNumberEncrypted: ByteArray? = null,
    @Column(name = "license_number", length = 30)
    val licenseNumber: String? = null,
    @Column(name = "license_security_code", length = 20)
    val licenseSecurityCode: String? = null,
    @Column(name = "passport_number_encrypted")
    val passportNumberEncrypted: ByteArray? = null,
    @Column(name = "birth_date")
    val birthDate: LocalDate? = null,
    @Column(name = "serial_number", length = 30)
    val serialNumber: String? = null,
    @Column(name = "issue_date")
    val issueDate: LocalDate? = null,
    @Column(name = "expire_date")
    val expireDate: LocalDate? = null,
    @Column(name = "id_card_s3_key", length = 200)
    val idCardS3Key: String? = null,
    @Column(name = "id_card_mime", length = 50)
    val idCardMime: String? = null,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: OffsetDateTime,
) {
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime? = null

    fun isExpired(now: OffsetDateTime = OffsetDateTime.now()): Boolean = now.isAfter(expiresAt)
}
