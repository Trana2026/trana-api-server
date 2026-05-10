package com.trana.terms

import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface TermsVersionRepository : JpaRepository<TermsVersion, Long> {
    /**
     * 현재 활성(시행됨) 약관 목록 — type별 가장 최신 effective version.
     * 시행 시점 < now() 인 것 중 type별 최신.
     */
    fun findByEffectiveAtBeforeOrderByEffectiveAtDesc(effectiveAt: Instant): List<TermsVersion>

    fun findByTypeAndVersion(
        type: TermsType,
        version: String,
    ): TermsVersion?
}
