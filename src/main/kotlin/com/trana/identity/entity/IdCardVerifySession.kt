package com.trana.identity.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.time.LocalDate

/**
 * 신분증 OCR → Verify 사이 임시 세션.
 *
 * - PK = NCP Document API requestId (uniqueness 보장)
 * - TTL 10분 (expiresAt 경과 시 cleanup task가 삭제)
 * - 평문 식별번호는 BYTEA로 암호화 보관 (Verify 호출 시 복호화 → NCP)
 * - 신분증 사진은 S3 (key + mime만 보관, 바이트는 S3)
 */
@Entity
@Table(name = "id_card_verify_sessions")
@Suppress("LongParameterList")
class IdCardVerifySession(
    @Id
    @Column(name = "request_id", nullable = false, length = 100)
    val requestId: String,
    @Column(name = "id_type", nullable = false, length = 30)
    val idType: String,
    @Column(name = "name", nullable = false, length = 100)
    val name: String,
    @Column(name = "personal_number_encrypted")
    val personalNumberEncrypted: ByteArray? = null,
    @Column(name = "license_number", length = 30)
    val licenseNumber: String? = null,
    @Column(name = "license_security_code", length = 20)
    val licenseSecurityCode: String? = null,
    @Column(name = "serial_number", length = 30)
    val serialNumber: String? = null,
    @Column(name = "issue_date")
    val issueDate: LocalDate? = null,
    @Column(name = "id_card_s3_key", length = 200)
    val idCardS3Key: String? = null,
    @Column(name = "id_card_mime", length = 50)
    val idCardMime: String? = null,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
) {
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null

    fun isExpired(now: Instant = Instant.now()): Boolean = !now.isBefore(expiresAt)
}
