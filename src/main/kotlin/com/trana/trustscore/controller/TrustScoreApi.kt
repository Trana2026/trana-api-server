package com.trana.trustscore.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.trustscore.TrustScoreExamples
import com.trana.trustscore.dto.TrustScoreResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping

@Tag(name = "Trust Score", description = "신뢰 점수 (마이페이지 카드, 등급, 통계)")
interface TrustScoreApi {
    @Operation(
        summary = "신뢰 점수 카드 조회",
        description = """
마이페이지 사용자 카드 + 통계 카드용 응답.

응답 필드:
- trustScore (0~100) : 현재 점수
- trustGrade : 등급 enum (NEWBIE/NORMAL/TRUST/EXCELLENT/BEST)
- trustGradeLabel : 등급 한글 라벨 (새내기/일반/신뢰/우수/최우수)
- completedContractCount : 양측 서명 완료 누적 계약 건수 (통계 1)
- warrantyProvidedCount : 판매자 보증 제공 + SIGNED 누적 (통계 2)
- fraudReportReceivedCount : 본인이 신고 당한 건 중 사기 확인 누적 (통계 3 "분쟁 여부")

신규 가입자 default = 35점 (NORMAL "일반").
    """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [
                    Content(
                        schema = Schema(implementation = TrustScoreResponse::class),
                        examples = [
                            ExampleObject(
                                name = "일반 (신뢰 등급)",
                                value = TrustScoreExamples.TRUST_SCORE_RESPONSE,
                            ),
                            ExampleObject(
                                name = "신규 (새내기)",
                                value = TrustScoreExamples.TRUST_SCORE_RESPONSE_NEWBIE,
                            ),
                            ExampleObject(
                                name = "최우수",
                                value = TrustScoreExamples.TRUST_SCORE_RESPONSE_BEST,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "토큰 없음 / 만료 / 위변조",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "사용자 없음 (USER_404)",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @GetMapping("/trust-score")
    fun getMyTrustScore(
        @Parameter(hidden = true) userId: Long,
    ): TrustScoreResponse
}
