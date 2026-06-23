package com.trana.trustscore.service

import com.trana.trustscore.dto.TrustScoreResponse
import com.trana.user.UserException
import com.trana.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 신뢰 점수 도메인 서비스.
 *
 * Phase 1 — 마이페이지 카드 조회만 (read-only).
 * Phase 2+ 에서 다음 추가:
 * - applyDelta(...) : 점수 변동 + trust_score_events INSERT + User counter +1
 * - SIGNED listener (ContractStatusChangedEvent)
 * - 사기 판정 listener (DisputeResolvedEvent)
 * - 등급 변경 감지 + FCM 발송 (NotificationDispatchService 위임)
 */
@Service
@Transactional(readOnly = true)
class TrustScoreService(
    private val userRepository: UserRepository,
) {
    /** 본인 신뢰 점수 카드 조회 — GET /v1/users/me/trust-score */
    fun getMyTrustScore(userId: Long): TrustScoreResponse {
        val user =
            userRepository.findById(userId).orElseThrow {
                UserException.NotFound(userId.toString())
            }
        val grade = user.trustGrade
        return TrustScoreResponse(
            trustScore = user.trustScore,
            trustGrade = grade,
            trustGradeLabel = grade.label,
            completedContractCount = user.completedContractCount,
            warrantyProvidedCount = user.warrantyProvidedCount,
            fraudReportReceivedCount = user.fraudReportReceivedCount,
        )
    }
}
