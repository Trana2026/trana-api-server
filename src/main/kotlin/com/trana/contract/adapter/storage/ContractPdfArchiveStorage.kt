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
     *
     * disposition:
     * - [Disposition.INLINE] : 앱/브라우저 내 inline 렌더링 (SHARED ~ SIGNED — 미리보기만)
     * - [Disposition.ATTACHMENT] : 파일 다운로드 다이얼로그 (COMPLETED 이후 — 거래 완료 후 보존본)
     * - filename 은 ATTACHMENT 일 때만 의미 있음 (브라우저 저장 시 표시될 이름)
     *
     * S3 의 `response-content-disposition` 응답 헤더 override 사용 → 같은 객체에서 단계별 분기.
     */
    fun presignGet(
        s3Key: String,
        disposition: Disposition = Disposition.INLINE,
        filename: String? = null,
    ): String {
        val responseContentDisposition =
            when (disposition) {
                Disposition.INLINE -> {
                    "inline"
                }

                Disposition.ATTACHMENT -> {
                    val safeName = filename ?: "contract.pdf"
                    """attachment; filename="$safeName""""
                }
            }
        val getRequest =
            GetObjectRequest
                .builder()
                .bucket(props.bucket)
                .key(s3Key)
                .responseContentDisposition(responseContentDisposition)
                .build()
        val presignRequest =
            GetObjectPresignRequest
                .builder()
                .signatureDuration(Duration.ofMinutes(props.presignedGetTtlMinutes))
                .getObjectRequest(getRequest)
                .build()
        return s3Presigner.presignGetObject(presignRequest).url().toString()
    }

    /**
     * S3 객체 InputStream 직접 노출 (서버측 zip 빌더 등 backend internal use).
     * 호출자가 use {} 로 close 책임.
     */
    fun openStream(s3Key: String): java.io.InputStream {
        val request =
            GetObjectRequest
                .builder()
                .bucket(props.bucket)
                .key(s3Key)
                .build()
        return s3Client.getObject(request)
    }

    enum class Disposition { INLINE, ATTACHMENT }

    val bucket: String get() = props.bucket

    val presignedGetTtlSeconds: Long get() = props.presignedGetTtlMinutes * SECONDS_PER_MINUTE

    companion object {
        private const val PDF_CONTENT_TYPE = "application/pdf"
        private const val SECONDS_PER_MINUTE = 60L
    }
}
