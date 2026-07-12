package com.trana.contract.service

import com.trana.contract.dto.RiskSignalsResponse
import com.trana.contract.entity.Contract
import com.trana.dispute.repository.DisputeRecordRepository
import com.trana.user.entity.User
import com.trana.user.repository.UserRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 서명 시 위험 신호 + 거래 상대방 신뢰 정보 계산.
 *
 * 위험 신호:
 * - hasReportHistory : 상대가 다른 계약에서 활성 신고 받은 적 있음
 * - trustScoreZero : 상대 trust_score == 0 ("주의 거래자" 배지, 명세 2.5.1)
 *
 * 상대방 신뢰 정보:
 * - counterpartyTrustScore : 0~100 (counterparty 없는 단계면 null)
 * - counterpartyTrustGrade : NEWBIE/NORMAL/TRUST/EXCELLENT/BEST
 *
 * 호출: getDetail (Contract 상세 응답에 포함) — frontend 가 서명 팝업 경고 + 신뢰도 표시.
 *
 * Sprint E 에서 counterpartyIsMinor / 미성년자 서명 안내 문구 판별 필드 추가 예정.
 */
@Component
class RiskSignalsCalculator(
    private val counterpartyResolver: CounterpartyResolver,
    private val userRepository: UserRepository,
    private val disputeRecordRepository: DisputeRecordRepository,
) {
    @Transactional(readOnly = true)
    fun calculate(
        contract: Contract,
        viewerUserId: Long,
    ): RiskSignalsResponse {
        val counterpartId = counterpartyResolver.resolveCounterpartUserId(contract, viewerUserId)
        val counterpart: User? = counterpartId?.let { userRepository.findById(it).orElse(null) }
        return RiskSignalsResponse(
            hasReportHistory = counterpartId?.let { disputeRecordRepository.existsReportAgainstUser(it) } ?: false,
            trustScoreZero = counterpart?.trustScore == 0,
            counterpartyTrustScore = counterpart?.trustScore,
            counterpartyTrustGrade = counterpart?.trustGrade,
        )
    }
}
