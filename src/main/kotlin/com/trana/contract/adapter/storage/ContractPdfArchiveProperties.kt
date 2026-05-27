package com.trana.contract.adapter.storage

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * PDF 영구 보존 버킷 설정 — trana-pdf-archive-{env}.
 *
 * - bucket 레벨: Versioning + Object Lock + Default retention (COMPLIANCE 5y)
 * - 코드는 PUT/GET 만 — DELETE 권한 없음 (IAM 에서도 차단)
 * - Default retention 이 버킷 레벨에 설정되어 있으므로 PUT 시 별도 헤더 불필요
 */
@ConfigurationProperties(prefix = "trana.contract.pdf-archive")
data class ContractPdfArchiveProperties(
    val bucket: String,
    val presignedGetTtlMinutes: Long = 10,
)
