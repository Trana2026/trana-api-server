package com.trana.contract.service

import com.trana.terms.entity.TermsType
import com.trana.terms.entity.TermsVersion
import com.trana.terms.repository.TermsVersionRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 계약 도메인 필수 약관 로더 — 단일 진실원천 (refactor j 잔여).
 *
 * - CONTRACT_AGREEMENT (계약 동의) + ELECTRONIC_SIGNATURE (전자서명 동의) 2종 필수
 * - 이전: ContractStatusService + ContractStatusCommitter 양쪽에 동일 로직 복제 (refactor d 임시 처리)
 * - termsVersionRepository.findActiveByType (DB DISTINCT ON) + 캐시 (refactor gg) 활용
 */
@Component
class ContractTermsLoader(
    private val termsVersionRepository: TermsVersionRepository,
) {
    @Transactional(readOnly = true)
    fun load(): List<TermsVersion> {
        val allActive = termsVersionRepository.findActiveByType(Instant.now())
        val picked =
            CONTRACT_TERM_TYPES.mapNotNull { type ->
                allActive.firstOrNull { it.type == type }
            }
        check(picked.size == CONTRACT_TERM_TYPES.size) {
            "계약 도메인 약관 시드 누락 — V10 마이그레이션 확인 필요"
        }
        return picked
    }

    companion object {
        private val CONTRACT_TERM_TYPES =
            listOf(TermsType.CONTRACT_AGREEMENT, TermsType.ELECTRONIC_SIGNATURE)
    }
}
