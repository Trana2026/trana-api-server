package com.trana.contract.service

import com.trana.user.UserException
import com.trana.user.dto.BlockUserResponse
import com.trana.user.service.UserBlockService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 계약 상대방 차단 오케스트레이션 — 계약 접근 검증 + 상대 해석 + 차단 위임.
 *
 * 계약 화면(상세/카드) 기반 UI 대응: 계약 publicCode 만으로 상대를 해석해 차단한다.
 */
@Service
class ContractBlockService(
    private val accessGuard: ContractAccessGuard,
    private val counterpartyResolver: CounterpartyResolver,
    private val userBlockService: UserBlockService,
) {
    @Transactional
    fun blockCounterparty(
        requesterUserId: Long,
        publicCode: String,
    ): BlockUserResponse {
        val contract = accessGuard.loadAccessible(publicCode, requesterUserId)
        val counterpartyId =
            counterpartyResolver.resolveCounterpartUserId(contract, requesterUserId)
                ?: throw UserException.BlockNoCounterparty(publicCode)
        return userBlockService.block(
            blockerUserId = requesterUserId,
            blockedUserId = counterpartyId,
            reason = "CONTRACT",
        )
    }
}
