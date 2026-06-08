package com.trana.dispute.dto

import com.trana.dispute.entity.DisputeRecord
import com.trana.dispute.entity.DisputeStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

/**
 * 신고 접수 요청.
 * - reason: 한 줄 요약 (100자 제한, DB chk_dispute_records 와 align)
 * - detail: 상세 내용 (자유 텍스트, 길이 제한 없음 — DB 는 TEXT)
 */
@Schema(description = "신고 접수 요청")
data class DisputeReportRequest(
    @field:Schema(description = "신고 사유 (한 줄 요약)", example = "물건 상태가 설명과 다름", maxLength = 100)
    @field:NotBlank
    @field:Size(max = REASON_MAX)
    val reason: String,
    @field:Schema(
        description = "신고 상세 내용 (자유 텍스트)",
        example = "사진과 달리 액정 멍이 있고 배터리 효율 70%대로 보임. 환불 요청 했으나 응답 없음.",
    )
    @field:NotBlank
    val detail: String,
) {
    companion object {
        const val REASON_MAX = DisputeRecord.REASON_MAX_LENGTH
    }
}

/**
 * 신고 단일 응답.
 * - 양측(신고자/피신고자) 다 조회 가능 — reporterUserId 직접 노출 X (보복 방지)
 * - isMine 으로 본인 신고 여부만 표시
 */
@Schema(description = "신고 정보")
data class DisputeResponse(
    @field:Schema(description = "신고 id", example = "42")
    val disputeId: Long,
    @field:Schema(description = "신고 사유 (한 줄)", example = "물건 상태가 설명과 다름")
    val reason: String,
    @field:Schema(description = "신고 상세 내용", example = "사진과 달리 액정 멍이 있음...")
    val detail: String,
    @field:Schema(description = "신고 상태", example = "REPORTED")
    val status: DisputeStatus,
    @field:Schema(description = "신고 접수 시각 (ISO-8601)", example = "2026-06-08T10:30:00Z")
    val reportedAt: Instant,
    @field:Schema(description = "신고자 본인 취소 시각 (있을 때만)", example = "2026-06-08T11:00:00Z")
    val cancelledAt: Instant?,
    @field:Schema(description = "본인이 접수한 신고인지", example = "true")
    val isMine: Boolean,
) {
    companion object {
        fun from(
            record: DisputeRecord,
            viewerUserId: Long,
        ): DisputeResponse =
            DisputeResponse(
                disputeId = record.id!!,
                reason = record.reason,
                detail = record.detail,
                status = record.status,
                reportedAt = record.reportedAt!!,
                cancelledAt = record.cancelledAt,
                isMine = record.reporterUserId == viewerUserId,
            )
    }
}

/**
 * 신고 목록 응답.
 */
@Schema(description = "계약 단위 신고 목록")
data class DisputeListResponse(
    @field:Schema(description = "신고 목록 (최신순)")
    val disputes: List<DisputeResponse>,
)
