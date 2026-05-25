package com.trana.contract.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * AI 추출 결과 (gpt-4o-mini Vision).
 *
 * 사용자 동의 + AI 호출 1회 = row 1개.
 * 재추출 시 새 row INSERT (audit / 재현 / 분쟁 증거용 5년 보존).
 *
 * 불변식:
 * - 모든 필드 val (한 번 INSERT 후 불변)
 * - extractedJson 은 gpt-4o-mini raw 응답 그대로 (prefill 결과는 contract row 의 필드)
 * - attachmentIds 는 PostgreSQL bigint[] 컬럼에 native array 매핑
 */
@Entity
@Table(name = "contract_ai_extractions")
@Suppress("LongParameterList")
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
    @Column(name = "extracted_json", nullable = false, columnDefinition = "text")
    val extractedJson: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @CreationTimestamp
    @Column(name = "extracted_at", nullable = false, updatable = false)
    val extractedAt: Instant? = null

    companion object {
        fun create(
            contractId: Long,
            modelName: String,
            promptVersion: String,
            consentTextVersion: String,
            consentedAt: Instant,
            attachmentIds: List<Long>,
            extractedJson: String,
        ): ContractAiExtraction =
            ContractAiExtraction(
                contractId = contractId,
                modelName = modelName,
                promptVersion = promptVersion,
                consentTextVersion = consentTextVersion,
                consentedAt = consentedAt,
                attachmentIds = attachmentIds,
                extractedJson = extractedJson,
            )
    }
}
