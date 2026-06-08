package com.trana.contract.dto

import com.trana.contract.entity.CancellationStatus
import com.trana.contract.entity.ContractCancellationRequest
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

/**
 * 취소 요청 접수.
 * - reason: 한 줄 (DB chk 100자 align)
 * - detail: 자유 텍스트 (TEXT)
 */
@Schema(description = "취소 요청 접수 요청")
data class CancellationRequestRequest(
    @field:Schema(
        description = "취소 사유 (한 줄 요약)",
        example = "법정대리인 인증을 안한 사람입니다. 거래를 진행하기 어렵습니다.",
        maxLength = REASON_MAX,
    )
    @field:NotBlank
    @field:Size(max = REASON_MAX)
    val reason: String,
    @field:Schema(
        description = "취소 상세 내용 (자유 텍스트)",
        example = "보호자 동의 확인 단계에서 미인증으로 표시되어 안전한 거래 보장이 어렵습니다.",
    )
    @field:NotBlank
    val detail: String,
) {
    companion object {
        const val REASON_MAX = ContractCancellationRequest.REASON_MAX_LENGTH
    }
}

/**
 * 취소 요청 응답.
 * - 양측 (요청자 / 피요청자) 다 조회 가능
 * - isMine: 본인이 요청한 요청인지
 */
@Schema(description = "취소 요청 정보")
data class CancellationRequestResponse(
    @field:Schema(description = "요청 id", example = "42")
    val cancellationRequestId: Long,
    @field:Schema(description = "취소 사유", example = "법정대리인 인증을 안한 사람입니다.")
    val reason: String,
    @field:Schema(description = "취소 상세 내용", example = "보호자 동의 확인 단계에서...")
    val detail: String,
    @field:Schema(description = "요청 상태", example = "REQUESTED")
    val status: CancellationStatus,
    @field:Schema(description = "요청 시각 (ISO-8601)", example = "2026-06-08T10:30:00Z")
    val requestedAt: Instant,
    @field:Schema(description = "상대 확정 시각 (확정된 경우만)", example = "2026-06-08T10:45:00Z")
    val confirmedAt: Instant?,
    @field:Schema(description = "본인이 요청한 요청인지", example = "true")
    val isMine: Boolean,
) {
    companion object {
        fun from(
            request: ContractCancellationRequest,
            viewerUserId: Long,
        ): CancellationRequestResponse =
            CancellationRequestResponse(
                cancellationRequestId = request.id!!,
                reason = request.reason,
                detail = request.detail,
                status = request.status,
                requestedAt = request.requestedAt!!,
                confirmedAt = request.confirmedAt,
                isMine = request.requesterUserId == viewerUserId,
            )
    }
}
