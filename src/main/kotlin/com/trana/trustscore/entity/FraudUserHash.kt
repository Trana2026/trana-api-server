package com.trana.trustscore.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * 사기 확인 사용자 식별자 hash — WORM 영구 보존.
 *
 * 생성 시점:
 * - 탈퇴 시 user.fraudReportReceivedCount > 0 (사기 확인 이력 1회 이상) 면 INSERT
 * - withdrawnAt 은 탈퇴 시점 채움 (탈퇴 전 사기 user 도 row 있을 수 있도록 nullable)
 *
 * 보안 (명세 부록):
 * - user_id_hash = SHA-256(users.public_code) — 식별자 추적 (NCP/OAuth 호환)
 * - ci_hash = SHA-256(PASS ci) — PASS user 의 재가입 차단 + 휴대폰 변경 후에도 동일 신원 (PASS-8)
 * - 원본 PII (이름/전화/주민번호) 는 탈퇴 시 즉시 삭제 + hash 만 영구 보존
 * - B2B API 사기 조회 서비스 (W10+) 의 source
 */
@Entity
@Table(name = "fraud_user_hashes")
class FraudUserHash(
    @Column(name = "user_id_hash", nullable = false, unique = true, length = 64)
    val userIdHash: String,
    @Column(name = "ci_hash", length = 64)
    val ciHash: String? = null,
    @Column(name = "fraud_confirmed_at", nullable = false)
    val fraudConfirmedAt: Instant,
    @Column(nullable = false, columnDefinition = "text")
    val reason: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reporter_id_hashes", columnDefinition = "jsonb")
    val reporterIdHashes: List<String>? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "related_contract_public_codes", columnDefinition = "jsonb")
    val relatedContractPublicCodes: List<String>? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(name = "withdrawn_at")
    var withdrawnAt: Instant? = null
        protected set

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
        protected set

    /**
     * 탈퇴 시점 마킹. 탈퇴 전 사기 확인 user 도 row 있을 수 있어 별도 메서드.
     * WORM trigger 가 withdrawn_at 1회 set 후 immutable 강제 (V10 trigger).
     */
    fun markWithdrawn() {
        check(withdrawnAt == null) { "이미 탈퇴 처리된 사기 user" }
        this.withdrawnAt = Instant.now()
    }
}
