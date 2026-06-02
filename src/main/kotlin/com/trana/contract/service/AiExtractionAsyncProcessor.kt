package com.trana.contract.service

import com.trana.contract.adapter.openai.OpenAiVisionAdapter
import com.trana.contract.repository.ContractAiExtractionRepository
import com.trana.contract.repository.ContractRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * AI 추출 비동기 처리기.
 *
 * 흐름:
 * - Service.submit() 가 PENDING row INSERT + AiExtractionRequestedEvent 발행
 * - AFTER_COMMIT phase 에 이 listener 트리거 → row 가 commit 된 후라 정확히 보임
 * - @Async 로 별도 스레드 풀에 위임 → OpenAI 호출 7초 동안 caller 차단 X
 * - 성공: markSuccess + Contract.updateDraft(prefill)
 * - 실패: markFailed(errorMessage) — try/catch 내부 catch 라 tx rollback 없음
 *
 * 트랜잭션:
 * - AFTER_COMMIT 트리거 → 부모 tx 종료 후 시작
 * - @Transactional 로 listener 본인의 새 tx 시작
 * - markSuccess / markFailed / contract.updateDraft 변경은 더티 체킹으로 자동 flush
 */
@Component
class AiExtractionAsyncProcessor(
    private val aiExtractionRepository: ContractAiExtractionRepository,
    private val contractRepository: ContractRepository,
    private val visionAdapter: OpenAiVisionAdapter,
) {
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Suppress("TooGenericExceptionCaught")
    fun handle(event: AiExtractionRequestedEvent) {
        val extraction =
            aiExtractionRepository.findById(event.extractionId).orElse(null)
                ?: run {
                    log.warn(
                        "Extraction row missing on async dispatch (id={})",
                        event.extractionId,
                    )
                    return
                }

        try {
            val result = visionAdapter.extractPrefill(event.s3Keys)
            extraction.markSuccess(
                extractedJson = result.rawJson,
                promptTokens = result.usage.promptTokens,
                completionTokens = result.usage.completionTokens,
                totalTokens = result.usage.totalTokens,
                latencyMs = result.latencyMs,
            )
            val contract = contractRepository.findById(event.contractId).orElse(null)
            if (contract != null) {
                contract.updateDraft(
                    title = result.prefill.productName,
                    price = result.prefill.price,
                    conditionSummary = result.prefill.conditionSummary,
                    conditionDetails = result.prefill.conditionDetails,
                    tradingPlatform = result.prefill.tradingPlatform,
                )
            } else {
                log.warn(
                    "Contract row missing on async dispatch (contractId={})",
                    event.contractId,
                )
            }
        } catch (e: Exception) {
            log.error(
                "AI extraction failed (extractionId={})",
                event.extractionId,
                e,
            )
            extraction.markFailed(
                e.message?.take(MAX_ERROR_LENGTH)
                    ?: e::class.simpleName
                    ?: "AI 추출 실패",
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AiExtractionAsyncProcessor::class.java)
        private const val MAX_ERROR_LENGTH = 500
    }
}
