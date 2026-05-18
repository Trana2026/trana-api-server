package com.trana.terms.service

import com.trana.terms.TermsException
import com.trana.terms.entity.TermsVersion
import com.trana.terms.repository.TermsVersionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 약관 도메인 서비스 (조회 전용).
 *
 * - 활성 약관 = effectiveAt <= now 중 type별 최신 1개
 * - 동의(consents)는 별도 ConsentService
 */
@Service
@Transactional(readOnly = true)
class TermsService(
    private val termsVersionRepository: TermsVersionRepository,
) {
    /** 현재 시점 활성 약관 — type별 최신 1개. */
    fun findActiveTerms(now: Instant = Instant.now()): List<TermsVersion> =
        termsVersionRepository
            .findAllByEffectiveAtLessThanEqualOrderByEffectiveAtDesc(now)
            .groupBy { it.type }
            .map { (_, versions) -> versions.first() }

    /** 단일 약관 조회 — 없으면 NotFound. ConsentService에서 동의 시 약관 존재 검증용. */
    fun getById(termsVersionId: Long): TermsVersion =
        termsVersionRepository.findById(termsVersionId).orElseThrow {
            TermsException.NotFound(termsVersionId.toString())
        }
}
