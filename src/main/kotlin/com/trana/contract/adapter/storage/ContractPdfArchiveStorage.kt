package com.trana.contract.adapter.storage

import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.time.Duration

/**
 * 계약 PDF 영구 보존 S3 adapter.
 *
 * 버킷 정책 (trana-pdf-archive-{env}):
 * - Versioning ON (Object Lock 의 전제)
 * - Object Lock ON
 * - Default retention: COMPLIANCE 5y → PUT 시 자동 적용
 *
 * 코드 측 제약:
 * - DELETE 메서드 미노출 (IAM 에서도 차단)
 * - PUT / GET 만 — PDF 한 번 올린 이후 변경 불가
 *
 * 사용:
 * - ContractPdfService (W5 #20) 가 PDF 바이트 → uploadPdf
 * - 사용자 다운로드는 presignGet 으로 짧은 TTL URL 발급
 *
 * cf. ContractAttachmentStorage 는 사진용 (presign PUT + GET + delete + sha256).
 */
@Component
class ContractPdfArchiveStorage(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val props: ContractPdfArchiveProperties,
) {
    /**
     * PDF 바이트를 archive 버킷에 업로드.
     *
     * 버킷의 Default retention (COMPLIANCE 5y) 가 자동 적용됨 → 별도 헤더 불필요.
     * Versioning ON 이므로 같은 s3Key 로 다시 PUT 해도 새 버전 생성 (옛 버전 immutable 보존).
     */
    fun uploadPdf(
        s3Key: String,
        content: ByteArray,
    ) {
        val request =
            PutObjectRequest
                .builder()
                .bucket(props.bucket)
                .key(s3Key)
                .contentType(PDF_CONTENT_TYPE)
                .build()
        s3Client.putObject(request, RequestBody.fromBytes(content))
    }

    /**
     * 사용자 PDF 다운로드용 presigned GET URL.
     *
     * TTL 짧게 (기본 10분) — 사용자가 즉시 다운로드.
     */
    fun presignGet(s3Key: String): String {
        val getRequest =
            GetObjectRequest
                .builder()
                .bucket(props.bucket)
                .key(s3Key)
                .build()
        val presignRequest =
            GetObjectPresignRequest
                .builder()
                .signatureDuration(Duration.ofMinutes(props.presignedGetTtlMinutes))
                .getObjectRequest(getRequest)
                .build()
        return s3Presigner.presignGetObject(presignRequest).url().toString()
    }

    val bucket: String get() = props.bucket

    val presignedGetTtlSeconds: Long get() = props.presignedGetTtlMinutes * SECONDS_PER_MINUTE

    companion object {
        private const val PDF_CONTENT_TYPE = "application/pdf"
        private const val SECONDS_PER_MINUTE = 60L
    }
}
