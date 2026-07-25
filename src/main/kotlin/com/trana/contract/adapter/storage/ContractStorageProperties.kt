package com.trana.contract.adapter.storage

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 계약 도메인 객체 저장소 설정 — trana-archive-{env} 버킷 전용.
 *
 * - bucket: 환경별 버킷명 (trana-archive-dev / -prod)
 * - presignedUploadTtlMinutes: 클라이언트 PUT URL 만료 (기본 10분)
 * - presignedGetTtlMinutes: 서버/OpenAI 즉시 fetch 용 GET URL 만료 (기본 5분)
 * - presignedGetClientTtlMinutes: 사용자 화면 노출용 GET URL 만료 (기본 60분) —
 *   썸네일/상세 사진이 재요청돼도 만료 안 되도록. presigned URL 최대 TTL 은 7일(SigV4, static 키).
 * - maxAttachmentSizeBytes: 사진 1장 최대 크기 — Service 단계에서 검증 (10MB)
 *
 * StorageProperties (temp 버킷) 와 분리 — IAM 권한 셋이 W5+ 에서 갈라지고
 * 버킷 lifecycle / Object Lock 정책도 archive 만 적용되므로 도메인 격리가 안전.
 */
@ConfigurationProperties(prefix = "trana.contract.storage")
data class ContractStorageProperties(
    val bucket: String,
    val presignedUploadTtlMinutes: Long = 10,
    val presignedGetTtlMinutes: Long = 5,
    val presignedGetClientTtlMinutes: Long = 60,
    val maxAttachmentSizeBytes: Long = DEFAULT_MAX_ATTACHMENT_SIZE_BYTES,
) {
    companion object {
        private const val DEFAULT_MAX_ATTACHMENT_SIZE_BYTES: Long = 10L * 1024 * 1024 // 10 MB
    }
}
