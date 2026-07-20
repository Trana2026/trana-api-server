package com.trana.terms.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(name = "terms_versions")
class TermsVersion(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: TermsType,
    @Column(nullable = false, length = 20)
    val version: String,
    @Column(nullable = false, length = 200)
    val title: String,
    @Column(name = "content_url", nullable = false, columnDefinition = "TEXT")
    val contentUrl: String,
    @Column(name = "content_hash", nullable = false, length = 64)
    val contentHash: String,
    @Column(name = "effective_at", nullable = false)
    val effectiveAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null
}

enum class TermsType {
    SERVICE,
    PRIVACY,
    THIRD_PARTY,
    MARKETING,
    LOCATION,
    CONTRACT_AGREEMENT,
    ELECTRONIC_SIGNATURE,
}

/**
 * 약관 조회 컨텍스트 — `GET /v1/terms?context=…` 필터 + 계약 서명 필수 약관 SoT.
 *
 * - CONTRACT: 계약 서명 단계 필수 약관. 현재 ELECTRONIC_SIGNATURE 1종
 *   (CONTRACT_AGREEMENT 는 서명 화면·기록에서 제거 — plan 1-2).
 *
 * 이 매핑이 계약 필수 약관의 단일 진실원천 — ContractTermsLoader 가 재사용.
 */
enum class TermsContext(
    val types: List<TermsType>,
) {
    CONTRACT(listOf(TermsType.ELECTRONIC_SIGNATURE)),
}
