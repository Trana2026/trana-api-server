package com.trana.terms

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional(readOnly = true)
class TermsService(private val termsVersionRepository: TermsVersionRepository) {
    /**
     * 현재 시행 중인 약관 목록 — type별 최신 1개씩.
     * effective_at <= now() 인 약관 중 type별 가장 최근 시행 버전.
     */
    fun getActiveTerms(): List<TermsVersion> {
        val now = Instant.now()
        return termsVersionRepository
            .findByEffectiveAtBeforeOrderByEffectiveAtDesc(now)
            .groupBy { it.type }
            .mapValues { (_, versions) -> versions.first() }
            .values
            .toList()
    }

    fun getById(id: Long): TermsVersion =
        termsVersionRepository.findById(id).orElseThrow { TermsException.NotFound(id) }
}
