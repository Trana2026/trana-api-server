package com.trana.terms.repository

import com.trana.terms.entity.TermsVersion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface TermsVersionRepository : JpaRepository<TermsVersion, Long> {
    /**
     * 활성 약관 — type 별 최신 effective row 1개씩 (refactor gg).
     *
     * PostgreSQL DISTINCT ON 으로 DB 가 type 별 첫 row 만 반환 → 메모리 풀스캔 제거.
     */
    @Query(
        value = """
              SELECT DISTINCT ON (type) *
              FROM terms_versions
              WHERE effective_at <= :now
              ORDER BY type, effective_at DESC
          """,
        nativeQuery = true,
    )
    fun findActiveByType(
        @Param("now") now: Instant,
    ): List<TermsVersion>
}
