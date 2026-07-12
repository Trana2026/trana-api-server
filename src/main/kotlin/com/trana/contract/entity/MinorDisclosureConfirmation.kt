package com.trana.contract.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnTransformer
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

/**
 * 미성년자와 거래하는 상대(성인)의 서명 전 위험 고지 확인 audit.
 *
 * - 한 계약에 한 party 한 번만 (UNIQUE contract_id, party_user_id) — 재확인 시 Service 에서 UPSERT
 * - ip 는 PostgreSQL INET 타입 (Hibernate 문자열 매핑)
 * - 분쟁 시 고지 입증 유일 수단 (이용약관 제32조 제2항)
 * - 문구 자체는 코드 상수 (MinorDisclosureTemplate) — 여기는 버전 문자열만 저장
 */
@Entity
@Table(name = "minor_disclosure_confirmations")
class MinorDisclosureConfirmation(
    @Column(name = "contract_id", nullable = false)
    val contractId: Long,
    @Column(name = "party_user_id", nullable = false)
    val partyUserId: Long,
    @Column(name = "template_version", nullable = false, length = 20)
    val templateVersion: String,
    @Column(name = "disclosed_at", nullable = false)
    val disclosedAt: Instant,
    @ColumnTransformer(write = "?::inet")
    @Column(name = "ip", columnDefinition = "inet")
    val ip: String? = null,
    @Column(name = "user_agent", columnDefinition = "TEXT")
    val userAgent: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @CreationTimestamp
    @Column(name = "confirmed_at", nullable = false, updatable = false)
    val confirmedAt: Instant? = null
}
