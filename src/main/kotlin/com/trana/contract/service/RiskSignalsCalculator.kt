package com.trana.contract.service

import com.trana.contract.dto.RiskSignalsResponse
import com.trana.contract.entity.Contract
import com.trana.dispute.repository.DisputeRecordRepository
import com.trana.user.entity.AgeGroup
import com.trana.user.entity.User
import com.trana.user.repository.UserRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 서명 시 위험 신호 계산 (W7 Phase A').
 *
 * - guardianNotConsented: 상대가 미성년 + user.guardianVerifiedAt == null
 * - hasReportHistory: 상대가 다른 계약에서 활성(REPORTED) 신고 받은 적 있음
 *
 * 호출 사이트:
 * - getDetail (계약 상세 응답에 포함) — frontend 가 서명 팝업 경고 + 취소 CTA 활성 결정
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
        return RiskSignalsResponse(
            guardianNotConsented = isCounterpartGuardianNotConsented(counterpartId),
            hasReportHistory = counterpartId?.let { disputeRecordRepository.existsReportAgainstUser(it) } ?: false,
        )
    }

    private fun isCounterpartGuardianNotConsented(counterpartId: Long?): Boolean {
        val counterpart: User =
            counterpartId?.let { userRepository.findById(it).orElse(null) } ?: return false
        return counterpart.ageGroup == AgeGroup.MINOR && counterpart.guardianVerifiedAt == null
    }
}
