package com.trana.contract.service

import com.trana.contract.ContractException
import com.trana.contract.adapter.openai.ExtractedPrefill
import com.trana.contract.adapter.openai.OpenAiProperties
import com.trana.contract.adapter.openai.OpenAiUsage
import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractAiExtraction
import com.trana.contract.entity.ContractStatus
import com.trana.contract.entity.ExtractionStatus
import com.trana.contract.repository.ContractAiExtractionRepository
import com.trana.contract.repository.ContractAttachmentRepository
import com.trana.contract.repository.ContractRepository
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
) {
    fun submit(
        publicCode: String,
        userId: Long,
        attachmentIds: List<Long>,
        consentedAt: Instant,
    ): AiExtractionStatusView {
        val contract = loadOwnedDraft(publicCode, userId)

        if (attachmentIds.size !in MIN_IMAGES..MAX_IMAGES) {
            throw ContractException.AiImageCountInvalid(attachmentIds.size)
        }

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
        val contract = loadOwned(publicCode, userId)
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
        val contract = loadOwned(publicCode, userId)
        val extraction =
            aiExtractionRepository.findFirstByContractIdOrderByExtractedAtDesc(contract.id!!)
                ?: return null
        return toStatusView(extraction)
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

    private fun loadOwnedDraft(
        publicCode: String,
        userId: Long,
    ): Contract {
        val contract = loadOwned(publicCode, userId)
        if (contract.status != ContractStatus.DRAFT) {
            throw ContractException.NotDraft(publicCode, contract.status.name)
        }
        return contract
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
