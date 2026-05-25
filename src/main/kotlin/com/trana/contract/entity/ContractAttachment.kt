package com.trana.contract.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

/**
 * 계약 첨부 파일 (게시글 스크린샷 1~7장).
 *
 * - S3 archive 버킷 (3년 보존, EXIF 미저장)
 * - presigned URL 로 client → S3 직접 업로드 후 metadata 등록
 *
 * 불변식:
 * - 한 번 등록된 메타는 변경 불가 (모든 필드 val) — 삭제는 계약 soft delete + S3 라이프사이클
 * - sortOrder 는 UI 순서 (0-based)
 */
@Entity
@Table(name = "contract_attachments")
@Suppress("LongParameterList")
class ContractAttachment(
    @Column(name = "contract_id", nullable = false)
    val contractId: Long,
    @Column(name = "s3_key", nullable = false, length = 500)
    val s3Key: String,
    @Column(name = "original_filename", length = 255)
    val originalFilename: String? = null,
    @Column(name = "content_type", length = 100)
    val contentType: String? = null,
    @Column(name = "size_bytes")
    val sizeBytes: Long? = null,
    @Column(name = "sort_order", nullable = false)
    val sortOrder: Int = 0,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    val uploadedAt: Instant? = null

    companion object {
        fun create(
            contractId: Long,
            s3Key: String,
            originalFilename: String?,
            contentType: String?,
            sizeBytes: Long?,
            sortOrder: Int,
        ): ContractAttachment =
            ContractAttachment(
                contractId = contractId,
                s3Key = s3Key,
                originalFilename = originalFilename,
                contentType = contentType,
                sizeBytes = sizeBytes,
                sortOrder = sortOrder,
            )
    }
}
