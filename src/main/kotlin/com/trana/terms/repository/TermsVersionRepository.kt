package com.trana.terms.repository

import com.trana.terms.entity.TermsVersion
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface TermsVersionRepository : JpaRepository<TermsVersion, Long> {
    /** 현재 시점에 시행 중인 약관 (effective_at ≤ now). type별 최신 1개 선택은 Service에서. */
    fun findAllByEffectiveAtLessThanEqualOrderByEffectiveAtDesc(now: Instant): List<TermsVersion>
}
