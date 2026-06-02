package com.trana.contract.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.Instant

@Schema(description = "첨부 업로드 presign 요청 (1단계)")
data class PresignAttachmentRequest(
    @field:NotBlank
    @field:Pattern(
        regexp = "^image/(jpeg|png|webp|heic|heif)$",
        message = "image/jpeg, image/png, image/webp, image/heic, image/heif 만 지원합니다",
    )
    @field:Schema(description = "업로드할 이미지 MIME (S3 서명에 포함)", example = "image/jpeg")
    val contentType: String,
)

@Schema(description = "presigned PUT URL 응답 — 클라이언트가 이 URL 로 직접 S3 PUT")
data class PresignAttachmentResponse(
    @field:Schema(description = "PUT 전용 presigned URL (TTL 10분, contentType 서명됨)")
    val uploadUrl: String,
    @field:Schema(description = "S3 객체 키 — 2단계 register 시 동일 값 전달")
    val s3Key: String,
    @field:Schema(description = "URL 만료 시각 (이후 PUT 은 403)")
    val expiresAt: Instant,
)

@Schema(description = "첨부 메타 등록 요청 (2단계 — S3 PUT 완료 후)")
data class RegisterAttachmentRequest(
    @field:NotBlank
    @field:Schema(description = "presign 응답의 s3Key 그대로", example = "contracts/Vh7sK2x9Pq3R/attachments/abc-uuid")
    val s3Key: String,
    @field:Size(max = FILENAME_MAX_LENGTH)
    @field:Schema(description = "사용자 단말의 원본 파일명 (검색/감사용)", example = "screenshot-01.jpg")
    val originalFilename: String? = null,
    @field:Pattern(regexp = "^image/[a-z0-9.+-]+$")
    @field:Schema(description = "업로드된 이미지 MIME", example = "image/jpeg")
    val contentType: String? = null,
    @field:PositiveOrZero
    @field:Schema(description = "업로드된 파일 크기 (bytes)", example = "524288")
    val sizeBytes: Long? = null,
)

@Schema(description = "첨부 단건 응답")
data class AttachmentResponse(
    @field:Schema(description = "첨부 id (DELETE/AI 추출 시 사용)", example = "101")
    val id: Long,
    val s3Key: String,
    val originalFilename: String?,
    val contentType: String?,
    val sizeBytes: Long?,
    @field:Schema(
        description = "S3 객체 SHA-256 hex (분쟁 증거 / PDF 본문 해시 입력)",
        example = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    )
    val sha256: String,
    @field:Schema(description = "UI 표시 순서 (0-based)", example = "0")
    val sortOrder: Int,
    @field:Schema(
        description = "presigned GET URL (캐러셀 inline 이미지 표시용, TTL 5분). 만료 시 list 재호출",
        example = "https://trana-contract-dev.s3.../attachments/.../0.jpg?X-Amz-...",
    )
    val viewUrl: String,
    val uploadedAt: Instant,
)

private const val FILENAME_MAX_LENGTH = 255
