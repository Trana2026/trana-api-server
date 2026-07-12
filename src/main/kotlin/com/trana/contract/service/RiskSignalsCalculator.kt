package com.trana.contract.service

import com.trana.contract.dto.RiskSignalsResponse
import com.trana.contract.entity.Contract
import com.trana.contract.repository.ContractPartyRepository
import com.trana.dispute.repository.DisputeRecordRepository
import com.trana.identity.entity.VerificationPurpose
import com.trana.identity.entity.VerificationStatus
import com.trana.identity.repository.IdentityVerificationRepository
import com.trana.user.entity.AgeGroup
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
    private val identityVerificationRepository: IdentityVerificationRepository,
    private val contractPartyRepository: ContractPartyRepository,
) {
    @Transactional(readOnly = true)
    fun calculate(
        contract: Contract,
        viewerUserId: Long,
    ): RiskSignalsResponse {
        val counterpartId = counterpartyResolver.resolveCounterpartUserId(contract, viewerUserId)
        val counterpart: User? = counterpartId?.let { userRepository.findById(it).orElse(null) }
        val verified =
            counterpartId?.let {
                identityVerificationRepository.existsByUserIdAndPurposeAndStatus(
                    it,
                    VerificationPurpose.SIGNUP,
                    VerificationStatus.SUCCESS,
                )
            } ?: false
        val tradeCount = counterpartId?.let { contractPartyRepository.countCompletedByUserId(it) } ?: 0L
        val disputeCount = counterpartId?.let { disputeRecordRepository.countReportedAgainstUser(it) } ?: 0L
        val confirmedReportCount =
            counterpartId?.let { disputeRecordRepository.countConfirmedReportedAgainstUser(it) } ?: 0L
        return RiskSignalsResponse(
            hasReportHistory = counterpartId?.let { disputeRecordRepository.existsReportAgainstUser(it) } ?: false,
            trustScoreZero = counterpart?.trustScore == 0,
            counterpartyTrustScore = counterpart?.trustScore,
            counterpartyTrustGrade = counterpart?.trustGrade,
            counterpartyIsMinor = counterpart?.ageGroup == AgeGroup.MINOR,
            counterpartyVerified = verified,
            counterpartyTradeCount = tradeCount.toInt(),
            counterpartyDisputeCount = disputeCount.toInt(),
            counterpartyConfirmedReportCount = confirmedReportCount.toInt(),
        )
    }
}
