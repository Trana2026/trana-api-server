package com.trana.contract.adapter.openai

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * OpenAI gpt-4o-mini Vision API 설정.
 *
 * - apiKey: TRANA_OPENAI_API_KEY (Railway env / local yml)
 * - model: gpt-4o-mini (W4 고정, 추후 변경 시 contract_ai_extractions.model_name 으로 audit)
 * - promptVersion / consentTextVersion: docs/contract.md v1 (변경 시 v2 로 증가)
 * - imageUrlTtlMinutes: archive 버킷 presigned GET URL TTL (OpenAI 호출 동안만 유효)
 * - timeoutSeconds: HTTP 호출 timeout
 */
@ConfigurationProperties(prefix = "trana.contract.openai")
data class OpenAiProperties(
    val apiKey: String,
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-4o-mini",
    val promptVersion: String = "v1",
    val consentTextVersion: String = "v1",
    val imageUrlTtlMinutes: Long = 5,
    val timeoutSeconds: Long = 60,
)
