package com.trana.guardian

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

/**
 * 보호자 마스터. trana 계정 없음 (anonymous KYC).
 *
 * - identifierHash로 동일 보호자 식별 (SHA-256(주민번호 등))
 * - KYC 정보(name/birthDate)는 매번 새 identity_verifications row에 기록 (재사용 X)
 * - 한 Guardian이 여러 미성년자 보호 가능 (guardian_relations N개)
 */
@Entity
@Table(name = "guardians")
class Guardian(
    @Column(name = "public_code", nullable = false, unique = true, length = 20)
    val publicCode: String,
    @Column(name = "identifier_hash", nullable = false, unique = true, length = 64)
    val identifierHash: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null
}
