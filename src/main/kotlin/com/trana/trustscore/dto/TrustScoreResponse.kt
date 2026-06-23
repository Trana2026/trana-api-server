package com.trana.trustscore.dto

import com.trana.user.entity.TrustGrade
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "마이페이지 신뢰 점수 카드 응답 — 본인 점수 + 등급 + 통계 3종")
data class TrustScoreResponse(
    @field:Schema(description = "신뢰 점수 (0~100)", example = "67")
    val trustScore: Int,
    @field:Schema(
        description = "등급 enum (NEWBIE 새내기 / NORMAL 일반 / TRUST 신뢰 / EXCELLENT 우수 / BEST 최우수)",
        example = "TRUST",
    )
    val trustGrade: TrustGrade,
    @field:Schema(description = "등급 한글 라벨", example = "신뢰")
    val trustGradeLabel: String,
    @field:Schema(description = "양측 서명 완료 누적 계약 건수 (\"누적 계약 건수\" 통계)", example = "24")
    val completedContractCount: Int,
    @field:Schema(description = "판매자 보증 제공 + SIGNED 누적 (\"보증 횟수\" 통계)", example = "7")
    val warrantyProvidedCount: Int,
    @field:Schema(
        description = "본인이 신고 당한 건 중 사기 확인 누적 (\"분쟁 여부\" 통계)",
        example = "1",
    )
    val fraudReportReceivedCount: Int,
)
