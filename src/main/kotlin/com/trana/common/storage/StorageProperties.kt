package com.trana.common.storage

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * S3 (또는 호환 Object Storage) 접근 설정 — application.yml의 trana.storage.s3.* 매핑.
 *
 * - bucket: 환경별 버킷명 (예: trana-temp-dev)
 * - region: AWS 리전 (예: ap-northeast-2)
 * - accessKey/secretKey: IAM 사용자 자격증명 (least privilege)
 */
@ConfigurationProperties(prefix = "trana.storage.s3")
data class StorageProperties(
    val bucket: String,
    val region: String,
    val accessKey: String,
    val secretKey: String,
)
