package com.trana.contract.service

import com.trana.common.util.PublicCodeGenerator
import com.trana.contract.ContractException
import com.trana.contract.entity.ConsentType
import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractParty
import com.trana.contract.entity.ContractStatus
import com.trana.contract.entity.DeliveryType
import com.trana.contract.entity.PartyType
import com.trana.contract.repository.ContractPartyRepository
import com.trana.contract.repository.ContractRepository
import com.trana.user.entity.AgeGroup
import com.trana.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 계약 DRAFT 단계 서비스 — 생성/조회/수정/삭제/목록.
 *
 * 권한:
 * - 모든 mutate 는 creatorUserId 본인만
 * - 조회/목록도 본인만 (counterparty 는 W5+ separate endpoint)
 *
 * 상태:
 * - DRAFT 에서만 수정/삭제 허용 (Entity 가 강제)
 *
 * consentType 결정 (가입 흐름과 일치):
 * - ADULT user → NOT_APPLICABLE
 * - MINOR user → GUARDIAN_REQUIRED (보호자 동의는 W4 별도 endpoint)
 */
@Service
@Transactional
class ContractDraftService(
    private val contractRepository: ContractRepository,
    private val contractPartyRepository: ContractPartyRepository,
    private val userRepository: UserRepository,
    private val publicCodeGenerator: PublicCodeGenerator,
) {
    fun createDraft(
        creatorUserId: Long,
        deliveryType: DeliveryType,
        creatorRole: PartyType,
    ): Contract {
        val user =
            userRepository.findById(creatorUserId).orElseThrow {
                IllegalStateException("계약 작성자 user 조회 실패 (userId=$creatorUserId)")
            }

        val consentType =
            when (user.ageGroup) {
                AgeGroup.MINOR -> ConsentType.GUARDIAN_REQUIRED
                AgeGroup.ADULT -> ConsentType.NOT_APPLICABLE
                null -> throw ContractException.InvalidConsentType("ageGroup 미설정 user 는 계약 생성 불가")
            }

        val contract =
            Contract.createDraft(
                publicCode = publicCodeGenerator.generate(),
                creatorUserId = creatorUserId,
                deliveryType = deliveryType,
                consentType = consentType,
            )
        val saved = contractRepository.save(contract)

        val party =
            ContractParty.create(
                contractId = saved.id!!,
                userId = creatorUserId,
                partyType = creatorRole,
            )
        contractPartyRepository.save(party)

        return saved
    }

    @Transactional(readOnly = true)
    fun getDraft(
        publicCode: String,
        userId: Long,
    ): Contract = loadOwned(publicCode, userId)

    fun updateDraft(
        publicCode: String,
        userId: Long,
        title: String? = null,
        price: Long? = null,
        conditionSummary: String? = null,
        conditionDetails: String? = null,
        location: String? = null,
        deliveryType: DeliveryType? = null,
    ): Contract {
        val contract = loadOwned(publicCode, userId)
        ensureDraft(contract)
        contract.updateDraft(
            title = title,
            price = price,
            conditionSummary = conditionSummary,
            conditionDetails = conditionDetails,
            location = location,
            deliveryType = deliveryType,
        )
        return contract
    }

    fun softDelete(
        publicCode: String,
        userId: Long,
    ) {
        val contract = loadOwned(publicCode, userId)
        ensureDraft(contract)
        contract.softDelete()
    }

    @Transactional(readOnly = true)
    fun listMyContracts(
        userId: Long,
        status: ContractStatus? = null,
    ): List<Contract> = contractRepository.findAllByCreator(userId, status)

    private fun loadOwned(
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

    private fun ensureDraft(contract: Contract) {
        if (contract.status != ContractStatus.DRAFT) {
            throw ContractException.NotDraft(contract.publicCode, contract.status.name)
        }
    }
}
