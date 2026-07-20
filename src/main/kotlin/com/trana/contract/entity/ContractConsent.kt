package com.trana.contract.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

/**
 * 계약 도메인 약관 동의 audit (WORM — insert-only).
 *
 * - `user_consents` (가입 약관) 와 별도. 계약 단위 — ELECTRONIC_SIGNATURE 1종 = user·contract 당 1 row
 *   (CONTRACT_AGREEMENT 는 서명 필수에서 제거 — plan 1-2)
 * - 양측 (생성자 + 수신자) 각자 서명 직전에 동의 → 한 계약에 user_id 2명 × 1 = 최대 2 row
 *
 * 불변식:
 * - 모든 필드 immutable (val) — audit 보존
 */
@Entity
@Table(name = "contract_consents")
class ContractConsent(
    @Column(name = "contract_id", nullable = false)
    val contractId: Long,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "term_id", nullable = false)
    val termId: Long,
    @Column(name = "term_version", nullable = false, length = 20)
    val termVersion: String,
    @Column(name = "consenter_ip", length = 45)
    val consenterIp: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @CreationTimestamp
    @Column(name = "consented_at", nullable = false, updatable = false)
    val consentedAt: Instant? = null

    companion object {
        fun create(
            contractId: Long,
            userId: Long,
            termId: Long,
            termVersion: String,
            consenterIp: String? = null,
        ): ContractConsent =
            ContractConsent(
                contractId = contractId,
                userId = userId,
                termId = termId,
                termVersion = termVersion,
                consenterIp = consenterIp,
            )
    }
}
