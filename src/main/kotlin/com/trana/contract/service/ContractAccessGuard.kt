package com.trana.contract.service

import com.trana.contract.ContractException
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

    /** preview 진입 검증 — DRAFT 또는 REVISION_REQUESTED 허용 (수정 요청 상태에서 PATCH 후 재미리보기 지원). */
    fun ensurePreviewable(contract: Contract) {
        if (contract.status != ContractStatus.DRAFT && contract.status != ContractStatus.REVISION_REQUESTED) {
            throw ContractException.NotDraft(contract.publicCode, contract.status.name)
        }
    }

    /** loadOwned + IN_PROGRESS/DRAFT 검증 — 첨부 / AI 추출 진입 시 (refactor h). */
    fun loadOwnedEditable(
        publicCode: String,
        userId: Long,
    ): Contract {
        val contract = loadOwned(publicCode, userId)
        ensureEditable(contract)
        return contract
    }

    /** 수정 가능 상태 검증 (IN_PROGRESS / DRAFT — updateDraft / softDelete 진입 시) */
    fun ensureEditable(contract: Contract) {
        if (contract.status != ContractStatus.IN_PROGRESS && contract.status != ContractStatus.DRAFT) {
            throw ContractException.NotDraft(contract.publicCode, contract.status.name)
        }
    }

    /**
     * 수정 가능 상태 검증 (IN_PROGRESS / DRAFT / REVISION_REQUESTED) — updateDraft 진입 시.
     * REVISION_REQUESTED 허용: 수정 후 reshare 흐름 (DRAFT 경유 X).
     * ensureEditable 는 softDelete 등 다른 흐름용 — IN_PROGRESS/DRAFT 만.
     */
    fun ensureUpdatable(contract: Contract) {
        if (contract.status != ContractStatus.IN_PROGRESS &&
            contract.status != ContractStatus.DRAFT &&
            contract.status != ContractStatus.REVISION_REQUESTED
        ) {
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
        val creatorPartyExists =
            contractPartyRepository.findFirstByContractIdAndUserId(
                contract.id!!,
                contract.creatorUserId,
            ) != null
        if (!creatorPartyExists) missing.add("creatorRole")
        if (missing.isNotEmpty()) {
            throw ContractException.NotReadyEligible(contract.publicCode, missing.joinToString(", "))
        }
    }
}
