package com.trana.contract.service

import com.trana.common.web.WebUrlBuilder
import com.trana.contract.ContractCancellationException
import com.trana.contract.adapter.kakao.CancellationRequestedMessage
import com.trana.contract.adapter.kakao.KakaoAlimtalkClient
import com.trana.contract.entity.CancellationStatus
import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractCancellationRequest
import com.trana.contract.entity.ContractStatus
import com.trana.contract.repository.ContractCancellationRequestRepository
import com.trana.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 계약 취소 요청 서비스 (W7 Phase A').
 *
 * - request: SHARED / RECEIVER_SIGNED 상태에서 서명 요청 받은 측이 취소 요청
 * - confirm: 상대 측이 확정 → CANCELLED 전이 (A'-4c)
 * - revoke: 요청자 본인이 자기 요청 취소 → previousStatus 복구 (SHARED / RECEIVER_SIGNED)
 *
 * 알림톡 발송 (상대 측) 은 request 시점만.
 */
@Service
@Transactional
class ContractCancellationService(
    private val accessGuard: ContractAccessGuard,
    private val cancellationRepository: ContractCancellationRequestRepository,
    private val counterpartyResolver: CounterpartyResolver,
    private val userRepository: UserRepository,
    private val kakaoAlimtalkClient: KakaoAlimtalkClient,
    private val webUrlBuilder: WebUrlBuilder,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /**
     * 취소 요청 접수.
     *
     * @param publicCode 대상 계약
     * @param requesterUserId 요청자 (서명 요청 받은 측만)
     * @param reason 취소 사유 (한 줄)
     * @param detail 취소 상세 내용
     * @param requesterIp audit IP
     */
    fun request(
        publicCode: String,
        requesterUserId: Long,
        reason: String,
        detail: String,
        requesterIp: String?,
    ): ContractCancellationRequest {
        val contract = accessGuard.loadAccessible(publicCode, requesterUserId)
        ensureRequestable(contract)
        ensureEligibleRequester(contract, requesterUserId)
        ensureNoActiveRequest(contract)

        val fromStatus = contract.status

        val request =
            cancellationRepository.save(
                ContractCancellationRequest(
                    contractId = contract.id!!,
                    requesterUserId = requesterUserId,
                    reason = reason,
                    detail = detail,
                    requesterIp = requesterIp,
                    previousStatus = contract.status,
                ),
            )

        contract.markCancelRequested()

        eventPublisher.publishEvent(
            ContractStatusChangedEvent(
                contractId = contract.id,
                fromStatus = fromStatus,
                toStatus = ContractStatus.CANCEL_REQUESTED,
                actorUserId = requesterUserId,
                reason = reason,
            ),
        )

        sendCancellationRequestedAlimtalk(contract, requesterUserId, request)
        return request
    }

    /**
     * 상대 측 취소 확정.
     *
     * @param publicCode 대상 계약
     * @param userId 확정자 (요청자가 아닌 측만)
     */
    fun confirm(
        publicCode: String,
        userId: Long,
    ) {
        val contract = accessGuard.loadAccessible(publicCode, userId)
        val request =
            cancellationRepository.findFirstByContractIdAndStatus(
                contract.id!!,
                CancellationStatus.REQUESTED,
            ) ?: throw ContractCancellationException.NotFound(publicCode)

        if (request.requesterUserId == userId) {
            throw ContractCancellationException.SelfConfirm(publicCode, userId)
        }

        val fromStatus = contract.status

        request.confirmByCounterparty(userId)
        contract.markCancelled()

        eventPublisher.publishEvent(
            ContractStatusChangedEvent(
                contractId = contract.id,
                fromStatus = fromStatus,
                toStatus = ContractStatus.CANCELLED,
                actorUserId = userId,
                reason = "양측 취소 확정",
            ),
        )
    }

    /**
     * 요청자 본인이 자기 취소 요청을 되돌림.
     * - contract.status → previousStatus (SHARED 또는 RECEIVER_SIGNED) 복구
     * - request.status → REVOKED (audit)
     *
     * @param publicCode 대상 계약
     * @param userId 요청자 (본인 검증)
     */
    fun revoke(
        publicCode: String,
        userId: Long,
    ) {
        val contract = accessGuard.loadAccessible(publicCode, userId)
        val request =
            cancellationRepository.findFirstByContractIdAndStatus(
                contract.id!!,
                CancellationStatus.REQUESTED,
            ) ?: throw ContractCancellationException.NotFound(publicCode)

        if (request.requesterUserId != userId) {
            throw ContractCancellationException.NotEligibleRequester(publicCode, userId)
        }

        val fromStatus = contract.status
        val previousStatus = request.previousStatus

        request.markRevoked(userId)
        contract.markCancelRequestRevoked(previousStatus)

        eventPublisher.publishEvent(
            ContractStatusChangedEvent(
                contractId = contract.id,
                fromStatus = fromStatus,
                toStatus = previousStatus,
                actorUserId = userId,
                reason = "요청자 revoke",
            ),
        )
    }

    /**
     * 활성(REQUESTED) 취소 요청 조회.
     * @return 활성 요청 1건 또는 null (스펙: "취소 내용 확인 바텀시트" 데이터)
     */
    @Transactional(readOnly = true)
    fun findActive(
        publicCode: String,
        userId: Long,
    ): ContractCancellationRequest? {
        val contract = accessGuard.loadAccessible(publicCode, userId)
        return cancellationRepository.findFirstByContractIdAndStatus(
            contract.id!!,
            CancellationStatus.REQUESTED,
        )
    }

    private fun ensureRequestable(contract: Contract) {
        if (contract.status != ContractStatus.SHARED && contract.status != ContractStatus.RECEIVER_SIGNED) {
            throw ContractCancellationException.NotRequestable(contract.publicCode, contract.status.name)
        }
    }

    /**
     * 송신 측은 차단 (서명 요청 보낸 사람 입장).
     * - SHARED: creator 가 송신 → creator 차단
     * - RECEIVER_SIGNED: receiver 가 송신 → receiver 차단
     */
    private fun ensureEligibleRequester(
        contract: Contract,
        requesterUserId: Long,
    ) {
        val isCreator = contract.creatorUserId == requesterUserId
        val isEligible =
            when (contract.status) {
                ContractStatus.SHARED -> !isCreator
                ContractStatus.RECEIVER_SIGNED -> isCreator
                else -> false
            }
        if (!isEligible) {
            throw ContractCancellationException.NotEligibleRequester(contract.publicCode, requesterUserId)
        }
    }

    private fun ensureNoActiveRequest(contract: Contract) {
        if (cancellationRepository.existsByContractIdAndStatus(contract.id!!, CancellationStatus.REQUESTED)) {
            throw ContractCancellationException.AlreadyActive(contract.publicCode)
        }
    }

    private val log = LoggerFactory.getLogger(javaClass)

    private fun sendCancellationRequestedAlimtalk(
        contract: Contract,
        requesterUserId: Long,
        record: ContractCancellationRequest,
    ) {
        val recipientId = counterpartyResolver.resolveCounterpartUserId(contract, requesterUserId)
        if (recipientId == null) {
            log.warn(
                "[CANCEL] 피요청자 미상 — 알림톡 skip (publicCode={}, requesterUserId={})",
                contract.publicCode,
                requesterUserId,
            )
            return
        }
        val recipient = userRepository.findById(recipientId).orElse(null)
        if (recipient?.name == null || recipient.phone == null) {
            log.warn(
                "[CANCEL] 피요청자 정보 불완전 — 알림톡 skip (publicCode={}, recipientId={})",
                contract.publicCode,
                recipientId,
            )
            return
        }
        kakaoAlimtalkClient.sendCancellationRequested(
            CancellationRequestedMessage(
                recipientName = recipient.name!!,
                recipientPhone = recipient.phone!!,
                contractTitle = contract.title ?: "",
                requestedAt = record.requestedAt!!,
                detailUrl = webUrlBuilder.contractDetail(contract.publicCode),
            ),
        )
    }
}
