package com.trana.contract.service

import com.trana.common.util.TokenGenerator
import com.trana.contract.ContractException
import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractConsent
import com.trana.contract.entity.ContractInvitation
import com.trana.contract.entity.ContractSignature
import com.trana.contract.entity.ContractStatus
import com.trana.contract.entity.PartyType
import com.trana.contract.repository.ContractConsentRepository
import com.trana.contract.repository.ContractInvitationRepository
import com.trana.contract.repository.ContractPartyRepository
import com.trana.contract.repository.ContractSignatureRepository
import com.trana.terms.entity.TermsVersion
import com.trana.user.entity.AgeGroup
import com.trana.user.entity.User
import com.trana.user.entity.UserStatus
import com.trana.user.repository.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 계약 상태 전이의 트랜잭션 경계 컴포넌트 (refactor d).
 *
 * - ContractStatusService 가 외부 I/O (PDF 렌더링 / S3 PUT / 알림톡) 흐름 orchestration
 * - 트랜잭션이 필요한 부분 (load + validate / persist + status log) 만 이 컴포넌트의 메서드 단위
 * - 외부 I/O 가 @Transactional 안에 머무르며 DB 커넥션 점유하던 결함 차단
 * - preview ↔ commit 사이 race 는 Contract.@Version (refactor f) 가 보호
 */
@Component
class ContractStatusCommitter(
    private val accessGuard: ContractAccessGuard,
    private val eventPublisher: ApplicationEventPublisher,
    private val contractPartyRepository: ContractPartyRepository,
    private val userRepository: UserRepository,
    private val contractConsentRepository: ContractConsentRepository,
    private val contractSignatureRepository: ContractSignatureRepository,
    private val termsLoader: ContractTermsLoader,
    private val invitationRepository: ContractInvitationRepository,
    private val tokenGenerator: TokenGenerator,
) {
    /** transitionToReady 의 외부 I/O 진입 전 preview — pre-check + PDF 렌더 input 생성. */
    @Transactional(readOnly = true)
    fun loadTransitionToReadyPreview(
        publicCode: String,
        userId: Long,
    ): ContractPdfRenderInput {
        val contract = accessGuard.loadOwned(publicCode, userId)
        accessGuard.ensureDraft(contract)
        accessGuard.validateReadyEligible(contract)
        return ContractPdfRenderInput(contract)
    }

    /** transitionToReady 의 외부 I/O 종료 후 commit — markReady + 이벤트 발행. */
    @Transactional
    fun commitTransitionToReady(
        publicCode: String,
        userId: Long,
        pdfS3Key: String,
        pdfSha256: String,
    ): Contract {
        val contract = accessGuard.loadOwned(publicCode, userId)
        accessGuard.ensureDraft(contract) // preview ~ commit 사이 상태 변동 재검증
        val from = contract.status
        contract.markReady(pdfS3Key = pdfS3Key, pdfSha256 = pdfSha256)
        eventPublisher.publishEvent(
            ContractStatusChangedEvent(
                contractId = contract.id!!,
                fromStatus = from,
                toStatus = contract.status,
                actorUserId = userId,
                reason = null,
            ),
        )
        return contract
    }

    @Suppress("ThrowsCount")
    @Transactional(readOnly = true)
    fun loadReceiverSignPreview(
        publicCode: String,
        userId: Long,
        agreedTermIds: List<Long>,
    ): ReceiverSignPreview {
        val contract = accessGuard.loadAccessible(publicCode, userId)
        if (contract.creatorUserId == userId) {
            throw ContractException.NotReceiver(publicCode, userId)
        }
        if (contract.status != ContractStatus.SHARED) {
            throw ContractException.NotInSharedState(publicCode, contract.status.name)
        }
        val contractId = contract.id!!

        val myParty =
            contractPartyRepository.findFirstByContractIdAndUserId(contractId, userId)
                ?: throw ContractException.NotReceiver(publicCode, userId)

        val expectedTerms = termsLoader.load()
        val expectedIds = expectedTerms.map { it.id!! }.toSet()
        if (agreedTermIds.toSet() != expectedIds) {
            throw ContractException.TermsMismatch(
                expected = expectedTerms.joinToString(", ") { "${it.type}(${it.version})" },
                actual = agreedTermIds,
            )
        }

        val receiver =
            userRepository.findById(userId).orElseThrow {
                IllegalStateException("수신자 user 조회 실패 (userId=$userId)")
            }

        return ReceiverSignPreview(
            contract = contract,
            partyType = myParty.partyType,
            receiverName = receiver.name ?: "(unknown)",
            receiverBirthDate = receiver.birthDate ?: "(unknown)",
            receiverPhone = receiver.phone ?: "(unknown)",
            receiverPassVerifiedAt = receiver.createdAt,
            expectedTerms = expectedTerms,
        )
    }

    @Suppress("LongParameterList", "ThrowsCount")
    @Transactional
    fun commitReceiverSign(
        publicCode: String,
        userId: Long,
        signatureBase64: String,
        expectedTerms: List<TermsVersion>,
        signerIp: String?,
        signerUserAgent: String?,
        pdfS3Key: String,
        pdfSha256: String,
    ): ReceiverSignCommitResult {
        val contract = accessGuard.loadAccessible(publicCode, userId)
        // preview ~ commit 사이 상태 변동 재검증
        if (contract.creatorUserId == userId) {
            throw ContractException.NotReceiver(publicCode, userId)
        }
        if (contract.status != ContractStatus.SHARED) {
            throw ContractException.NotInSharedState(publicCode, contract.status.name)
        }
        val contractId = contract.id!!
        val myParty =
            contractPartyRepository.findFirstByContractIdAndUserId(contractId, userId)
                ?: throw ContractException.NotReceiver(publicCode, userId)

        expectedTerms.forEach { term ->
            contractConsentRepository.save(
                ContractConsent.create(
                    contractId = contractId,
                    userId = userId,
                    termId = term.id!!,
                    termVersion = term.version,
                    consenterIp = signerIp,
                ),
            )
        }

        val signature =
            contractSignatureRepository.save(
                ContractSignature.create(
                    contractId = contractId,
                    userId = userId,
                    partyType = myParty.partyType,
                    signatureData = signatureBase64,
                    pdfVersionAtSign = contract.version,
                    pdfSha256AtSign = pdfSha256,
                    signerIp = signerIp,
                    signerUserAgent = signerUserAgent,
                ),
            )

        val from = contract.status
        contract.markReceiverSigned(pdfS3Key = pdfS3Key, pdfSha256 = pdfSha256)
        eventPublisher.publishEvent(
            ContractStatusChangedEvent(
                contractId = contractId,
                fromStatus = from,
                toStatus = contract.status,
                actorUserId = userId,
                reason = null,
            ),
        )

        return ReceiverSignCommitResult(
            contract = contract,
            receiverSignedAt = signature.signedAt ?: Instant.now(),
        )
    }

    @Suppress("ThrowsCount")
    @Transactional(readOnly = true)
    fun loadReceiverWarrantyPreview(
        publicCode: String,
        userId: Long,
        days: Int,
    ): ContractPdfRenderInput {
        val contract = accessGuard.loadAccessible(publicCode, userId)
        if (contract.creatorUserId == userId) {
            throw ContractException.NotReceiverSeller(publicCode, userId)
        }
        if (contract.status != ContractStatus.SHARED) {
            throw ContractException.NotInSharedState(publicCode, contract.status.name)
        }
        val party =
            contractPartyRepository.findFirstByContractIdAndUserId(contract.id!!, userId)
                ?: throw ContractException.NotReceiverSeller(publicCode, userId)
        if (party.partyType != PartyType.SELLER) {
            throw ContractException.NotReceiverSeller(publicCode, userId)
        }
        return ContractPdfRenderInput(contract = contract, warrantyDaysOverride = days)
    }

    @Suppress("ThrowsCount")
    @Transactional
    fun commitReceiverWarranty(
        publicCode: String,
        userId: Long,
        days: Int,
        pdfS3Key: String,
        pdfSha256: String,
    ): Contract {
        val contract = accessGuard.loadAccessible(publicCode, userId)
        // preview ~ commit 사이 상태 변동 재검증
        if (contract.creatorUserId == userId) {
            throw ContractException.NotReceiverSeller(publicCode, userId)
        }
        if (contract.status != ContractStatus.SHARED) {
            throw ContractException.NotInSharedState(publicCode, contract.status.name)
        }
        val party =
            contractPartyRepository.findFirstByContractIdAndUserId(contract.id!!, userId)
                ?: throw ContractException.NotReceiverSeller(publicCode, userId)
        if (party.partyType != PartyType.SELLER) {
            throw ContractException.NotReceiverSeller(publicCode, userId)
        }
        contract.applyReceiverWarranty(days = days, pdfS3Key = pdfS3Key, pdfSha256 = pdfSha256)
        return contract
    }

    @Suppress("ThrowsCount")
    @Transactional(readOnly = true)
    fun loadReshareReadyPreview(
        publicCode: String,
        userId: Long,
    ): ContractPdfRenderInput {
        val contract = accessGuard.loadOwned(publicCode, userId)
        if (contract.status != ContractStatus.REVISION_REQUESTED) {
            throw ContractException.NotInRevisionRequestedState(publicCode, contract.status.name)
        }
        accessGuard.validateReadyEligible(contract)
        return ContractPdfRenderInput(contract)
    }

    @Suppress("ThrowsCount")
    @Transactional
    fun commitReshare(
        publicCode: String,
        userId: Long,
        pdfS3Key: String,
        pdfSha256: String,
    ): ReshareCommitResult {
        val contract = accessGuard.loadOwned(publicCode, userId)
        // preview ~ commit 사이 상태 변동 재검증
        if (contract.status != ContractStatus.REVISION_REQUESTED) {
            throw ContractException.NotInRevisionRequestedState(publicCode, contract.status.name)
        }
        val previousInvitation =
            invitationRepository.findFirstByContractIdOrderByIdDesc(contract.id!!)
                ?: error("REVISION_REQUESTED 상태인데 이전 invitation row 가 없음 (contractId=${contract.id})")
        val newInvitation =
            invitationRepository.save(
                ContractInvitation.create(
                    contractId = contract.id,
                    token = tokenGenerator.generateContractInvitation(),
                    receiverName = previousInvitation.receiverName,
                    receiverPhone = previousInvitation.receiverPhone,
                ),
            )
        val from = contract.status
        contract.markReshared(pdfS3Key = pdfS3Key, pdfSha256 = pdfSha256)
        eventPublisher.publishEvent(
            ContractStatusChangedEvent(
                contractId = contract.id,
                fromStatus = from,
                toStatus = contract.status,
                actorUserId = userId,
                reason = "수정 완료 → 재공유",
            ),
        )
        return ReshareCommitResult(contract = contract, invitation = newInvitation)
    }

    @Suppress("ThrowsCount")
    @Transactional(readOnly = true)
    fun loadCreatorSignPreview(
        publicCode: String,
        userId: Long,
        agreedTermIds: List<Long>,
    ): CreatorSignPreview {
        val contract = accessGuard.loadOwned(publicCode, userId)
        if (contract.status != ContractStatus.RECEIVER_SIGNED) {
            throw ContractException.NotInReceiverSignedState(publicCode, contract.status.name)
        }
        val contractId = contract.id!!

        val parties = contractPartyRepository.findAllByContractId(contractId)
        val creatorParty =
            parties.firstOrNull { it.userId == userId }
                ?: error("creator party 없음 (contractId=$contractId)")
        val receiverParty =
            parties.firstOrNull { it.userId != userId }
                ?: error("receiver party 없음 (contractId=$contractId, RECEIVER_SIGNED 인데 party 1개?)")

        validateUserReady(receiverParty.userId)

        val expectedTerms = termsLoader.load()
        val expectedIds = expectedTerms.map { it.id!! }.toSet()
        if (agreedTermIds.toSet() != expectedIds) {
            throw ContractException.TermsMismatch(
                expected = expectedTerms.joinToString(", ") { "${it.type}(${it.version})" },
                actual = agreedTermIds,
            )
        }

        val receiverSignature =
            contractSignatureRepository.findByContractIdAndPartyType(contractId, receiverParty.partyType)
                ?: error("receiver signature 없음 (contractId=$contractId, RECEIVER_SIGNED 인데 row 없음)")

        val creator =
            userRepository.findById(userId).orElseThrow {
                IllegalStateException("생성자 user 조회 실패 (userId=$userId)")
            }
        val receiver =
            userRepository.findById(receiverParty.userId).orElseThrow {
                IllegalStateException("수신자 user 조회 실패 (userId=${receiverParty.userId})")
            }

        return CreatorSignPreview(
            contract = contract,
            creatorPartyType = creatorParty.partyType,
            creator =
                PartyDisplay(
                    name = creator.name ?: "(unknown)",
                    birthDate = creator.birthDate ?: "(unknown)",
                    phone = creator.phone ?: "(unknown)",
                    passVerifiedAt = creator.createdAt,
                ),
            receiver =
                PartyDisplay(
                    name = receiver.name ?: "(unknown)",
                    birthDate = receiver.birthDate ?: "(unknown)",
                    phone = receiver.phone ?: "(unknown)",
                    passVerifiedAt = receiver.createdAt,
                ),
            receiverSignatureBase64 = receiverSignature.signatureData,
            receiverSignedAt = receiverSignature.signedAt,
            expectedTerms = expectedTerms,
        )
    }

    @Suppress("LongParameterList", "LongMethod", "ThrowsCount")
    @Transactional
    fun commitCreatorSign(
        publicCode: String,
        userId: Long,
        signatureBase64: String,
        expectedTerms: List<TermsVersion>,
        signerIp: String?,
        signerUserAgent: String?,
        pdfS3Key: String,
        pdfSha256: String,
    ): CreatorSignCommitResult {
        val contract = accessGuard.loadOwned(publicCode, userId)
        // preview ~ commit 사이 상태 변동 재검증
        if (contract.status != ContractStatus.RECEIVER_SIGNED) {
            throw ContractException.NotInReceiverSignedState(publicCode, contract.status.name)
        }
        val contractId = contract.id!!

        val parties = contractPartyRepository.findAllByContractId(contractId)
        val creatorParty =
            parties.firstOrNull { it.userId == userId }
                ?: error("creator party 없음 (contractId=$contractId)")
        val receiverParty =
            parties.firstOrNull { it.userId != userId }
                ?: error("receiver party 없음 (contractId=$contractId)")

        val creator =
            userRepository.findById(userId).orElseThrow {
                IllegalStateException("생성자 user 조회 실패 (userId=$userId)")
            }
        val receiver =
            userRepository.findById(receiverParty.userId).orElseThrow {
                IllegalStateException("수신자 user 조회 실패 (userId=${receiverParty.userId})")
            }

        expectedTerms.forEach { term ->
            contractConsentRepository.save(
                ContractConsent.create(
                    contractId = contractId,
                    userId = userId,
                    termId = term.id!!,
                    termVersion = term.version,
                    consenterIp = signerIp,
                ),
            )
        }

        val signature =
            contractSignatureRepository.save(
                ContractSignature.create(
                    contractId = contractId,
                    userId = userId,
                    partyType = creatorParty.partyType,
                    signatureData = signatureBase64,
                    pdfVersionAtSign = contract.version,
                    pdfSha256AtSign = pdfSha256,
                    signerIp = signerIp,
                    signerUserAgent = signerUserAgent,
                ),
            )

        val from = contract.status
        contract.markSigned(pdfS3Key = pdfS3Key, pdfSha256 = pdfSha256)
        eventPublisher.publishEvent(
            ContractStatusChangedEvent(
                contractId = contractId,
                fromStatus = from,
                toStatus = contract.status,
                actorUserId = userId,
                reason = null,
            ),
        )

        return CreatorSignCommitResult(
            contract = contract,
            creator = creator,
            receiver = receiver,
            creatorSignedAt = signature.signedAt ?: Instant.now(),
        )
    }

    private fun validateUserReady(userId: Long) {
        val user =
            userRepository.findById(userId).orElseThrow {
                IllegalStateException("user 조회 실패 (userId=$userId)")
            }
        if (user.status != UserStatus.ACTIVE) {
            throw ContractException.UserNotReady(userId, "user.status=${user.status}")
        }
        if (user.ageGroup == AgeGroup.MINOR && user.guardianVerifiedAt == null) {
            throw ContractException.UserNotReady(userId, "미성년 보호자 검증 미완료")
        }
    }
}

data class ReceiverSignPreview(
    val contract: Contract,
    val partyType: PartyType,
    val receiverName: String,
    val receiverBirthDate: String,
    val receiverPhone: String,
    val receiverPassVerifiedAt: Instant?,
    val expectedTerms: List<TermsVersion>,
)

data class ReceiverSignCommitResult(
    val contract: Contract,
    val receiverSignedAt: Instant,
)

data class CreatorSignPreview(
    val contract: Contract,
    val creatorPartyType: PartyType,
    val creator: PartyDisplay,
    val receiver: PartyDisplay,
    val receiverSignatureBase64: String,
    val receiverSignedAt: Instant?,
    val expectedTerms: List<TermsVersion>,
)

data class PartyDisplay(
    val name: String,
    val birthDate: String,
    val phone: String,
    val passVerifiedAt: Instant? = null,
)

data class CreatorSignCommitResult(
    val contract: Contract,
    val creator: User,
    val receiver: User,
    val creatorSignedAt: Instant,
)

data class ReshareCommitResult(
    val contract: Contract,
    val invitation: ContractInvitation,
)
