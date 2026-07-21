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
    // 온보딩 (가입)
    SERVICE, // 서비스 이용약관 (동의)
    PRIVACY, // 개인정보 수집·이용 동의
    PRIVACY_POLICY, // 개인정보 처리방침 (열람 전용 — 동의 X)
    THIRD_PARTY, // 개인정보 제3자 제공 동의
    MARKETING, // 마케팅 정보 수신 동의 (선택)
    LOCATION, // 위치정보 이용 동의 (레거시 유지)

    // 계약
    CONTRACT_AGREEMENT, // 계약 동의 (서명 필수에서 제거, 레거시 유지 — plan 1-2)
    ELECTRONIC_SIGNATURE, // 전자서명 동의 (계약 서명 필수)

    // AI 자동기입 (AiAutofillNoticeDialog)
    AI_AUTOFILL_NOTICE, // AI 자동기입 면책 고지 (readonly 열람)
    AI_CROSS_BORDER, // AI 자동기입 국외이전 동의 (필수 체크, 개인정보보호법 §28-8)

    // 법정대리인 (대리인 웹)
    GUARDIAN_WARRANTY, // 본인확인·친권관계 보증 약관 (필수 동의)
    GUARDIAN_PRIVACY, // 개인정보 수집·이용 동의 (보호자용, 필수 동의)
    GUARDIAN_LEGAL_REP, // 법정대리인 확인 선언 (필수 동의, 상세 문서 없음)
}

/**
 * 약관 조회 컨텍스트 — `GET /v1/terms?context=…` 필터 + 도메인별 필수 약관 SoT.
 *
 * - CONTRACT: 계약 서명 단계 필수 약관. 현재 ELECTRONIC_SIGNATURE 1종
 *   (CONTRACT_AGREEMENT 는 서명 화면·기록에서 제거 — plan 1-2).
 *
 * 이 매핑이 각 흐름 필수 약관의 단일 진실원천 — ContractTermsLoader 등이 재사용.
 */
enum class TermsContext(
    val types: List<TermsType>,
) {
    CONTRACT(listOf(TermsType.ELECTRONIC_SIGNATURE)),
}
