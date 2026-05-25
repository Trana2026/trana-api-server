package com.trana.contract.adapter.storage

import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration
import java.time.Instant

/**
 * 계약 사진 (게시글 스크린샷) S3 archive 버킷 presigned upload URL 발급.
 *
 * 흐름:
 * - Service 가 contract 별 attachment slot 마다 s3Key 생성
 * - presignPut() → 클라이언트가 직접 S3 로 PUT (서버 트래픽 우회)
 * - 업로드 직후 클라이언트 → POST /v1/contracts/{id}/attachments 로 메타 등록
 *
 * 보안:
 * - URL 자체에 PUT 권한 + contentType + 만료 시각이 서명되어 있음
 * - 클라이언트가 contentType 을 다르게 PUT 하면 S3 가 거부
 * - 만료 후 PUT 은 403
 */
@Component
class ContractAttachmentStorage(
    private val s3Presigner: S3Presigner,
    private val props: ContractStorageProperties,
) {
    /**
     * PUT presigned URL 발급.
     *
     * @param s3Key 저장 경로 (예: contracts/{publicCode}/attachments/{nanoid}.jpg)
     * @param contentType 업로드할 MIME (image/jpeg / image/png)
     */
    fun presignPut(
        s3Key: String,
        contentType: String,
    ): PresignedUpload {
        val ttl = Duration.ofMinutes(props.presignedUploadTtlMinutes)
        val putRequest =
            PutObjectRequest
                .builder()
                .bucket(props.bucket)
                .key(s3Key)
                .contentType(contentType)
                .build()
        val presignRequest =
            PutObjectPresignRequest
                .builder()
                .signatureDuration(ttl)
                .putObjectRequest(putRequest)
                .build()
        val signed = s3Presigner.presignPutObject(presignRequest)
        return PresignedUpload(
            uploadUrl = signed.url().toString(),
            s3Key = s3Key,
            expiresAt = Instant.now().plus(ttl),
        )
    }

    val bucket: String get() = props.bucket

    val maxAttachmentSizeBytes: Long get() = props.maxAttachmentSizeBytes
}

/** Service / Controller 로 전달되는 presigned URL 결과. */
data class PresignedUpload(
    val uploadUrl: String,
    val s3Key: String,
    val expiresAt: Instant,
)
