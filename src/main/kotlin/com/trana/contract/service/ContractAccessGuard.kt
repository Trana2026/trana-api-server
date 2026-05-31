package com.trana.contract.service

import com.trana.contract.ContractException
import com.trana.contract.entity.ConsentType
import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractStatus
import com.trana.contract.repository.ContractPartyRepository
import com.trana.contract.repository.ContractRepository
import org.springframework.stereotype.Component

/**
 * 계약 접근/상태 검증 공유 helper.
 *
 * - ContractDraftService (createDraft / updateDraft / softDelete / preview)
 *   와 ContractStatusService (transitionToReady / share / finalize / cancel) 양쪽에서 호출
 * - 단일 진입점 = 권한/불변식 일관성 보장
 */
@Component
class ContractAccessGuard(
    private val contractRepository: ContractRepository,
    private val contractPartyRepository: ContractPartyRepository,
) {
    /** publicCode 로 조회 + 본인(creator) 검증. 삭제된 계약은 NotFound */
    fun loadOwned(
        publicCode: String,
        userId: Long,
    ): Contract {
        val contract =
            contractRepository.findByPublicCodeAndDeletedAtIsNull(publicCode)
                ?: throw ContractException.NotFound(publicCode)
        if (contract.creatorUserId != userId) {
            throw ContractException.NotOwner(publicCode, userId)
        }
        return contract
    }

    /** publicCode 로 조회 + 접근 권한 검증 (creator OR contract_parties 멤버). 삭제된 계약은 NotFound */
    fun loadAccessible(
        publicCode: String,
        userId: Long,
    ): Contract {
        val contract =
            contractRepository.findByPublicCodeAndDeletedAtIsNull(publicCode)
                ?: throw ContractException.NotFound(publicCode)
        if (contract.creatorUserId == userId) {
            return contract
        }
        val party = contractPartyRepository.findFirstByContractIdAndUserId(contract.id!!, userId)
        if (party != null) {
            return contract
        }
        throw ContractException.NotAccessible(publicCode, userId)
    }

    /** DRAFT 상태 검증 (markReady / previewPdf 진입 시 — 4 필드 완성 상태 확인) */
    fun ensureDraft(contract: Contract) {
        if (contract.status != ContractStatus.DRAFT) {
            throw ContractException.NotDraft(contract.publicCode, contract.status.name)
        }
    }

    /** 수정 가능 상태 검증 (IN_PROGRESS / DRAFT — updateDraft / softDelete 진입 시) */
    fun ensureEditable(contract: Contract) {
        if (contract.status != ContractStatus.IN_PROGRESS && contract.status != ContractStatus.DRAFT) {
            throw ContractException.NotDraft(contract.publicCode, contract.status.name)
        }
    }

    /** READY 전이 가능 검증 (markReady / previewPdf 공통) */
    fun validateReadyEligible(contract: Contract) {
        val missing = mutableListOf<String>()
        if (contract.title == null) missing.add("title")
        if (contract.price == null) missing.add("price")
        if (contract.conditionSummary == null) missing.add("conditionSummary")
        if (contract.conditionDetails == null) missing.add("conditionDetails")
        if (contract.deliveryType == null) missing.add("deliveryType")
        if (missing.isNotEmpty()) {
            throw ContractException.NotReadyEligible(contract.publicCode, missing.joinToString(", "))
        }
        if (contract.consentType == ConsentType.GUARDIAN_REQUIRED && contract.guardianConsentAt == null) {
            throw ContractException.GuardianConsentRequired(contract.publicCode)
        }
    }
}
