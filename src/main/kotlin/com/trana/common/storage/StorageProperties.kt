package com.trana.common.storage

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * AWS S3 클라이언트 자격증명 — application.yml 의 trana.storage.s3.* 매핑.
 *
 * S3Config 가 이 값으로 공통 S3Client / S3Presigner Bean 생성 →
 * Contract 첨부 (ContractAttachmentStorage) + Contract PDF archive 가 재사용.
 *
 * 버킷명은 도메인별 별도 config 로 관리:
 * - trana.contract.storage.bucket → 첨부
 * - trana.contract.pdf-archive.bucket → PDF archive
 */
@ConfigurationProperties(prefix = "trana.storage.s3")
data class StorageProperties(
    val region: String,
    val accessKey: String,
    val secretKey: String,
)
