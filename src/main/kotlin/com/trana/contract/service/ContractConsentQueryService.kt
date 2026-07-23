package com.trana.contract.service

import com.trana.contract.repository.ContractConsentRepository
import com.trana.terms.dto.MyConsentResponse
import com.trana.terms.entity.TermsType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 마이페이지 '동의한 약관 목록'에 계약 도메인 동의(contract_consents)를 노출하기 위한 조회 서비스.
 *
 * - 현재는 AI 국외이전 동의(AI_CROSS_BORDER)만 사용자 대면 목록에 노출.
 * - contract_consents 는 계약당 row 가 쌓이므로 term 당 최신 1건만 반환 (계약별 나열 X).
 * - user_consents(가입 약관)와 합치는 병합은 UserConsentController 가 수행.
 */
@Service
@Transactional(readOnly = true)
class ContractConsentQueryService(
    private val contractConsentRepository: ContractConsentRepository,
    private val termsLoader: ContractTermsLoader,
) {
    /** 사용자의 AI 국외이전 동의(있으면 최신 1건)를 MyConsentResponse 로. 없으면 빈 목록. */
    @Suppress("ReturnCount")
    fun findUserAiConsents(userId: Long): List<MyConsentResponse> {
        // 활성 AI_CROSS_BORDER 미시드/버전 이슈 시에도 마이페이지가 500 나지 않도록 nullable 조회.
        val term = termsLoader.findActiveOrNull(TermsType.AI_CROSS_BORDER) ?: return emptyList()
        val termId = term.id ?: return emptyList()
        val consent = contractConsentRepository.findFirstByUserIdAndTermIdOrderByConsentedAtDesc(userId, termId)
        val agreedAt = consent?.consentedAt ?: return emptyList()
        return listOf(
            MyConsentResponse(
                termsId = termId,
                type = term.type,
                version = term.version,
                title = term.title,
                agreedAt = agreedAt,
            ),
        )
    }
}
