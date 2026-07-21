package com.trana.contract.service

import com.trana.terms.entity.TermsContext
import com.trana.terms.entity.TermsType
import com.trana.terms.entity.TermsVersion
import com.trana.terms.repository.TermsVersionRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 계약 도메인 필수 약관 로더 — 단일 진실원천 (refactor j 잔여).
 *
 * - ELECTRONIC_SIGNATURE (전자서명 동의) 1종 필수
 *   (CONTRACT_AGREEMENT 는 서명 화면·기록에서 제거 — plan 1-2)
 * - 필수 타입 목록은 TermsContext.CONTRACT.types 가 SoT (GET /v1/terms?context=CONTRACT 와 동일)
 * - termsVersionRepository.findActiveByType (DB DISTINCT ON) + 캐시 (refactor gg) 활용
 */
@Component
class ContractTermsLoader(
    private val termsVersionRepository: TermsVersionRepository,
) {
    @Transactional(readOnly = true)
    fun load(): List<TermsVersion> {
        val requiredTypes = TermsContext.CONTRACT.types
        val allActive = termsVersionRepository.findActiveByType(Instant.now())
        val picked =
            requiredTypes.mapNotNull { type ->
                allActive.firstOrNull { it.type == type }
            }
        check(picked.size == requiredTypes.size) {
            "계약 도메인 약관 시드 누락 — terms_versions 확인 필요"
        }
        return picked
    }

    /** 특정 타입의 현재 활성 약관 1개 (예: AI_CROSS_BORDER). 없으면 예외. */
    @Transactional(readOnly = true)
    fun loadActive(type: TermsType): TermsVersion =
        termsVersionRepository
            .findActiveByType(Instant.now())
            .firstOrNull { it.type == type }
            ?: error("활성 약관 없음 — terms_versions 확인 필요 (type=$type)")
}
