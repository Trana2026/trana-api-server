package com.trana.contract.service

import com.trana.contract.ContractException
import com.trana.contract.adapter.openai.ExtractedPrefill
import com.trana.contract.adapter.openai.OpenAiProperties
import com.trana.contract.adapter.openai.OpenAiUsage
import com.trana.contract.adapter.openai.OpenAiVisionAdapter
import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractAiExtraction
import com.trana.contract.entity.ContractStatus
import com.trana.contract.repository.ContractAiExtractionRepository
import com.trana.contract.repository.ContractAttachmentRepository
import com.trana.contract.repository.ContractRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Instant

/**
 * 계약 AI 추출 서비스 — 1~2장 첨부 → OpenAI Vision → prefill 자동 반영.
 *
 * 흐름:
 * 1. DRAFT + creator 권한 검증
 * 2. attachmentIds 검증 (1~2장 + 본 계약 소속)
 * 3. OpenAiVisionAdapter 호출 → ExtractedPrefill + usage + latency
 * 4. ai_extractions INSERT (audit / 5년 보존)
 * 5. Contract.updateDraft() 로 prefill 4필드 자동 반영 (dirty checking)
 *
 * 정책:
 * - 재추출 시 새 row INSERT (기존 row 변경 X, 모든 필드 val)
 * - prefill 자동 반영 = 사용자가 다시 부르면 덮어쓰기 (UI 에서 확인 dialog 노출)
 * - 동의는 클라이언트 타임스탬프 신뢰 (W4 단순화, W7+ 정교화)
 */
@Service
@Transactional
class ContractAiExtractionService(
    private val contractRepository: ContractRepository,
    private val attachmentRepository: ContractAttachmentRepository,
    private val aiExtractionRepository: ContractAiExtractionRepository,
    private val visionAdapter: OpenAiVisionAdapter,
    private val openAiProps: OpenAiProperties,
    private val objectMapper: ObjectMapper,
) {
    fun extract(
        publicCode: String,
        userId: Long,
        attachmentIds: List<Long>,
        consentedAt: Instant,
    ): AiExtractionView {
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

        val result = visionAdapter.extractPrefill(s3Keys)

        val extraction =
            ContractAiExtraction.create(
                contractId = contract.id,
                modelName = openAiProps.model,
                promptVersion = openAiProps.promptVersion,
                consentTextVersion = openAiProps.consentTextVersion,
                consentedAt = consentedAt,
                attachmentIds = attachmentIds,
                extractedJson = result.rawJson,
                promptTokens = result.usage.promptTokens,
                completionTokens = result.usage.completionTokens,
                totalTokens = result.usage.totalTokens,
                latencyMs = result.latencyMs,
            )
        val saved = aiExtractionRepository.save(extraction)

        contract.updateDraft(
            title = result.prefill.productName,
            price = result.prefill.price,
            conditionSummary = result.prefill.conditionSummary,
            conditionDetails = result.prefill.conditionDetails,
        )

        return AiExtractionView(
            extractionId = saved.id!!,
            model = saved.modelName,
            promptVersion = saved.promptVersion,
            prefill = result.prefill,
            latencyMs = result.latencyMs,
            usage = result.usage,
            extractedAt = requireNotNull(saved.extractedAt) { "extractedAt 은 @CreationTimestamp 로 채워짐" },
        )
    }

    @Transactional(readOnly = true)
    fun getLatest(
        publicCode: String,
        userId: Long,
    ): AiExtractionView? {
        val contract = loadOwned(publicCode, userId)
        val extraction =
            aiExtractionRepository
                .findFirstByContractIdOrderByExtractedAtDesc(contract.id!!)
                ?: return null
        val prefill = objectMapper.readValue(extraction.extractedJson, ExtractedPrefill::class.java)
        return AiExtractionView(
            extractionId = requireNotNull(extraction.id),
            model = extraction.modelName,
            promptVersion = extraction.promptVersion,
            prefill = prefill,
            latencyMs = extraction.latencyMs,
            usage =
                OpenAiUsage(
                    promptTokens = extraction.promptTokens,
                    completionTokens = extraction.completionTokens,
                    totalTokens = extraction.totalTokens,
                ),
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

/**
 * AI 추출 응답 view — Controller 가 그대로 직렬화.
 *
 * - extractionId: ai_extractions row id (재현/audit 용)
 * - prefill: 자동 반영된 4필드
 * - latencyMs / usage: 클라이언트 모니터링 (Swagger Example 일치)
 */
data class AiExtractionView(
    val extractionId: Long,
    val model: String,
    val promptVersion: String,
    val prefill: ExtractedPrefill,
    val latencyMs: Long,
    val usage: OpenAiUsage,
    val extractedAt: Instant,
)
