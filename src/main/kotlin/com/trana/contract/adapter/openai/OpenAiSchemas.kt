package com.trana.contract.adapter.openai

/**
 * OpenAI Structured Outputs용 JSON Schema 정의.
 *
 * - strict: true 모드에서 사용 (모든 필드 required + additionalProperties: false 강제)
 * - ExtractedPrefill 과 1:1 매칭 (필드 변경 시 schema/DTO 같이 갱신)
 */
internal object OpenAiSchemas {
    val CONTRACT_PREFILL_SCHEMA: Map<String, Any> =
        mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to
                listOf(
                    "product_name",
                    "price",
                    "condition_summary",
                    "condition_details",
                ),
            "properties" to
                mapOf(
                    "product_name" to
                        mapOf(
                            "type" to "string",
                            "description" to "상품명 (예: '아이폰 15 Pro 256GB 블랙')",
                        ),
                    "price" to
                        mapOf(
                            "type" to "integer",
                            "description" to "거래 가격 (KRW, 정수). 추출 불가 시 0",
                        ),
                    "condition_summary" to
                        mapOf(
                            "type" to "string",
                            "description" to "상품 상태 요약 한 줄",
                        ),
                    "condition_details" to
                        mapOf(
                            "type" to "string",
                            "description" to "상태/하자/포함품 상세 설명",
                        ),
                ),
        )
}
