package com.trana.terms.service

import com.trana.terms.TermsException
import com.trana.terms.dto.TermsContentResponse
import com.trana.terms.entity.TermsVersion
import com.trana.terms.repository.TermsVersionRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 약관 도메인 서비스 (조회 전용).
 *
 * - 활성 약관 = effectiveAt <= now 중 type별 최신 1개
 * - 전문(마크다운)은 백엔드 리소스(classpath: terms/{slug}_{version}.md)에 저장. content_hash = 해당 파일 SHA-256
 * - 동의(consents)는 별도 ConsentService
 */
@Service
@Transactional(readOnly = true)
class TermsService(
    private val termsVersionRepository: TermsVersionRepository,
) {
    /** 현재 시점 활성 약관 — type별 최신 1개 (DB DISTINCT ON + Caffeine 10분 캐시, refactor gg). */
    @Cacheable("activeTerms")
    fun findActiveTerms(): List<TermsVersion> = termsVersionRepository.findActiveByType(Instant.now())

    /** 단일 약관 조회 — 없으면 NotFound. ConsentService에서 동의 시 약관 존재 검증용. */
    fun getById(termsVersionId: Long): TermsVersion =
        termsVersionRepository.findById(termsVersionId).orElseThrow {
            TermsException.NotFound(termsVersionId.toString())
        }

    /**
     * 약관 단건 + 전문(마크다운) 조회.
     * 전문은 리소스 파일에서 읽음. 리소스 없는 타입(LOCATION 등 레거시)은 NotFound.
     */
    fun getTermWithContent(termsVersionId: Long): TermsContentResponse {
        val term = getById(termsVersionId)
        val slug =
            term.type.name
                .lowercase()
                .replace('_', '-')
        val path = "terms/${slug}_${term.version}.md"
        val resource = ClassPathResource(path)
        if (!resource.exists()) throw TermsException.NotFound("content:$path")
        val content = resource.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        return TermsContentResponse(
            id = checkNotNull(term.id),
            type = term.type,
            version = term.version,
            title = term.title,
            contentHash = term.contentHash,
            content = content,
            effectiveAt = term.effectiveAt,
        )
    }
}
