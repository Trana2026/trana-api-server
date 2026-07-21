package com.trana.contract.service

import com.trana.contract.ContractException
import com.trana.contract.adapter.openai.ExtractedPrefill
import com.trana.contract.adapter.openai.OpenAiProperties
import com.trana.contract.adapter.openai.OpenAiUsage
import com.trana.contract.entity.ContractAiExtraction
import com.trana.contract.entity.ContractConsent
import com.trana.contract.entity.ExtractionStatus
import com.trana.contract.repository.ContractAiExtractionRepository
import com.trana.contract.repository.ContractAttachmentRepository
import com.trana.contract.repository.ContractConsentRepository
import com.trana.contract.repository.ContractRepository
import com.trana.terms.entity.TermsType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Instant

/**
 * 계약 AI 추출 서비스 — 비동기.
 *
 * 흐름:
 * 1. submit(): DRAFT + creator 검증 → 첨부 검증 → PENDING row INSERT → 이벤트 발행 → 즉시 응답
 * 2. AiExtractionAsyncProcessor 가 AFTER_COMMIT 에 트리거 → OpenAI 호출 → markSuccess / markFailed
 * 3. 프론트는 GET ./latest 또는 GET ./{id} 로 status 폴링
 *
 * 정책:
 * - 재추출 시 새 row INSERT (audit / 5년 보존)
 * - SUCCESS 시 AsyncProcessor 가 contract.updateDraft() 호출 (prefill 자동 반영)
 * - 동의는 클라이언트 타임스탬프 (W4 단순화, W7+ 정교화)
 */
@Service
@Transactional
class ContractAiExtractionService(
    private val contractRepository: ContractRepository,
    private val attachmentRepository: ContractAttachmentRepository,
    private val aiExtractionRepository: ContractAiExtractionRepository,
    private val openAiProps: OpenAiProperties,
    private val objectMapper: ObjectMapper,
    private val eventPublisher: ApplicationEventPublisher,
    private val accessGuard: ContractAccessGuard,
    private val termsLoader: ContractTermsLoader,
    private val contractConsentRepository: ContractConsentRepository,
) {
    fun submit(
        publicCode: String,
        userId: Long,
        attachmentIds: List<Long>,
        consentedAt: Instant,
        consenterIp: String? = null,
    ): AiExtractionStatusView {
        val contract = accessGuard.loadOwnedEditable(publicCode, userId)

        if (attachmentIds.size !in MIN_IMAGES..MAX_IMAGES) {
            throw ContractException.AiImageCountInvalid(attachmentIds.size)
        }

        // AI 국외이전 동의(개인정보보호법 §28-8, 필수) 를 계약 동의 audit 에 개별 기록 (재추출 멱등).
        // 면책 고지(AI_AUTOFILL_NOTICE, readonly)는 아래 consentTextVersion 으로 갈음.
        recordCrossBorderConsentIfAbsent(contract.id!!, userId, consenterIp)

        val contractAttachments = attachmentRepository.findAllByContractIdOrderBySortOrderAsc(contract.id!!)
        val attachmentMap = contractAttachments.associateBy { it.id!! }
        val selectedInOrder =
            attachmentIds.map { id ->
                attachmentMap[id] ?: throw ContractException.AttachmentNotFound(id)
            }
        val s3Keys = selectedInOrder.map { it.s3Key }

        val extraction =
            ContractAiExtraction.create(
                contractId = contract.id,
                modelName = openAiProps.model,
                promptVersion = openAiProps.promptVersion,
                consentTextVersion = openAiProps.consentTextVersion,
                consentedAt = consentedAt,
                attachmentIds = attachmentIds,
            )
        val saved = aiExtractionRepository.save(extraction)

        eventPublisher.publishEvent(
            AiExtractionRequestedEvent(
                extractionId = saved.id!!,
                contractId = contract.id,
                s3Keys = s3Keys,
            ),
        )

        return toStatusView(saved)
    }

    @Transactional(readOnly = true)
    fun getById(
        publicCode: String,
        extractionId: Long,
        userId: Long,
    ): AiExtractionStatusView {
        val contract = accessGuard.loadOwned(publicCode, userId)
        val extraction =
            aiExtractionRepository.findByIdAndContractId(extractionId, contract.id!!)
                ?: throw ContractException.AiExtractionNotFound(extractionId)
        return toStatusView(extraction)
    }

    @Transactional(readOnly = true)
    fun getLatest(
        publicCode: String,
        userId: Long,
    ): AiExtractionStatusView? {
        val contract = accessGuard.loadOwned(publicCode, userId)
        val extraction =
            aiExtractionRepository.findFirstByContractIdOrderByExtractedAtDesc(contract.id!!)
                ?: return null
        return toStatusView(extraction)
    }

    /**
     * AI 국외이전 동의(AI_CROSS_BORDER)를 contract_consents 에 1회 기록.
     * 재추출 시 같은 (contract, user, term) 중복 방지 — unique 제약 충돌 회피 위해 존재확인 후 INSERT.
     */
    private fun recordCrossBorderConsentIfAbsent(
        contractId: Long,
        userId: Long,
        consenterIp: String?,
    ) {
        val term = termsLoader.loadActive(TermsType.AI_CROSS_BORDER)
        val termId = requireNotNull(term.id)
        val already =
            contractConsentRepository
                .findAllByContractIdAndUserIdOrderByConsentedAtAsc(contractId, userId)
                .any { it.termId == termId }
        if (already) return
        contractConsentRepository.save(
            ContractConsent.create(
                contractId = contractId,
                userId = userId,
                termId = termId,
                termVersion = term.version,
                consenterIp = consenterIp,
            ),
        )
    }

    private fun toStatusView(extraction: ContractAiExtraction): AiExtractionStatusView {
        val prefill =
            extraction.extractedJson?.let {
                objectMapper.readValue(it, ExtractedPrefill::class.java)
            }
        val usage =
            if (extraction.status == ExtractionStatus.SUCCESS) {
                OpenAiUsage(
                    promptTokens = extraction.promptTokens!!,
                    completionTokens = extraction.completionTokens!!,
                    totalTokens = extraction.totalTokens!!,
                )
            } else {
                null
            }
        return AiExtractionStatusView(
            extractionId = requireNotNull(extraction.id),
            status = extraction.status,
            model = extraction.modelName,
            promptVersion = extraction.promptVersion,
            prefill = prefill,
            latencyMs = extraction.latencyMs,
            usage = usage,
            errorMessage = extraction.errorMessage,
            extractedAt = requireNotNull(extraction.extractedAt),
        )
    }

    companion object {
        private const val MIN_IMAGES = 1
        private const val MAX_IMAGES = 2
    }
}

/** getById / getLatest 응답 — status 에 따라 nullable 필드. */
data class AiExtractionStatusView(
    val extractionId: Long,
    val status: ExtractionStatus,
    val model: String,
    val promptVersion: String,
    val prefill: ExtractedPrefill?,
    val latencyMs: Long?,
    val usage: OpenAiUsage?,
    val errorMessage: String?,
    val extractedAt: Instant,
)

/** AFTER_COMMIT 에 AsyncProcessor 가 받는 이벤트. */
data class AiExtractionRequestedEvent(
    val extractionId: Long,
    val contractId: Long,
    val s3Keys: List<String>,
)
