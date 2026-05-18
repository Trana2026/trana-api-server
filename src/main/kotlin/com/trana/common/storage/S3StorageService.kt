package com.trana.common.storage

import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest

/**
 * AWS S3 (또는 S3 호환 endpoint) StorageService 구현.
 *
 * - sync API 사용 (KYC 흐름은 sync)
 * - bucket은 properties에서 (테스트/dev/prod 환경별 분리)
 */
@Service
class S3StorageService(
    private val s3Client: S3Client,
    private val props: StorageProperties,
) : StorageService {
    override fun put(
        key: String,
        bytes: ByteArray,
        contentType: String,
    ) {
        val request =
            PutObjectRequest
                .builder()
                .bucket(props.bucket)
                .key(key)
                .contentType(contentType)
                .build()
        s3Client.putObject(request, RequestBody.fromBytes(bytes))
    }

    override fun get(key: String): ByteArray {
        val request =
            GetObjectRequest
                .builder()
                .bucket(props.bucket)
                .key(key)
                .build()
        return s3Client.getObjectAsBytes(request).asByteArray()
    }

    override fun delete(key: String) {
        val request =
            DeleteObjectRequest
                .builder()
                .bucket(props.bucket)
                .key(key)
                .build()
        s3Client.deleteObject(request)
    }
}
