package com.trana.contract.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * AI 추출 결과 (gpt-4o-mini Vision) — 비동기 처리.
 *
 * 라이프사이클:
 * - 요청 시점에 PENDING 으로 INSERT (결과 5필드 null)
 * - 백그라운드 호출 완료 → markSuccess() 로 결과 채우며 SUCCESS 전이
 * - 호출 실패 → markFailed(errorMessage) 로 FAILED 전이
 *
 * 재추출 시 새 row INSERT (audit / 재현 / 분쟁 증거용 5년 보존).
 *
 * 불변식:
 * - PENDING 으로만 생성. create() 가 강제
 * - PENDING → SUCCESS / FAILED 만 가능 (역방향 X, 재전이 X). check() 가 강제
 * - SUCCESS 시 결과 5종 모두 채움 (markSuccess 시그니처가 강제)
 */
@Entity
@Table(name = "contract_ai_extractions")
class ContractAiExtraction(
    @Column(name = "contract_id", nullable = false)
    val contractId: Long,
    @Column(name = "model_name", nullable = false, length = 50)
    val modelName: String,
    @Column(name = "prompt_version", nullable = false, length = 20)
    val promptVersion: String,
    @Column(name = "consent_text_version", nullable = false, length = 20)
    val consentTextVersion: String,
    @Column(name = "consented_at", nullable = false)
    val consentedAt: Instant,
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "attachment_ids", nullable = false, columnDefinition = "bigint[]")
    val attachmentIds: List<Long>,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: ExtractionStatus = ExtractionStatus.PENDING
        protected set

    @Column(name = "extracted_json", columnDefinition = "text")
    var extractedJson: String? = null
        protected set

    @Column(name = "prompt_tokens")
    var promptTokens: Int? = null
        protected set

    @Column(name = "completion_tokens")
    var completionTokens: Int? = null
        protected set

    @Column(name = "total_tokens")
    var totalTokens: Int? = null
        protected set

    @Column(name = "latency_ms")
    var latencyMs: Long? = null
        protected set

    @Column(name = "error_message", columnDefinition = "text")
    var errorMessage: String? = null
        protected set

    @CreationTimestamp
    @Column(name = "extracted_at", nullable = false, updatable = false)
    val extractedAt: Instant? = null

    fun markSuccess(
        extractedJson: String,
        promptTokens: Int,
        completionTokens: Int,
        totalTokens: Int,
        latencyMs: Long,
    ) {
        check(status == ExtractionStatus.PENDING) {
            "Cannot transition from $status to SUCCESS"
        }
        this.status = ExtractionStatus.SUCCESS
        this.extractedJson = extractedJson
        this.promptTokens = promptTokens
        this.completionTokens = completionTokens
        this.totalTokens = totalTokens
        this.latencyMs = latencyMs
    }

    fun markFailed(errorMessage: String) {
        check(status == ExtractionStatus.PENDING) {
            "Cannot transition from $status to FAILED"
        }
        this.status = ExtractionStatus.FAILED
        this.errorMessage = errorMessage
    }

    companion object {
        fun create(
            contractId: Long,
            modelName: String,
            promptVersion: String,
            consentTextVersion: String,
            consentedAt: Instant,
            attachmentIds: List<Long>,
        ): ContractAiExtraction =
            ContractAiExtraction(
                contractId = contractId,
                modelName = modelName,
                promptVersion = promptVersion,
                consentTextVersion = consentTextVersion,
                consentedAt = consentedAt,
                attachmentIds = attachmentIds,
            )
    }
}

enum class ExtractionStatus {
    PENDING,
    SUCCESS,
    FAILED,
}
