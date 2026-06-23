package com.trana.dispute.service

import com.trana.common.web.WebUrlBuilder
import com.trana.contract.adapter.kakao.DisputeReportedMessage
import com.trana.contract.adapter.kakao.KakaoAlimtalkClient
import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractStatus
import com.trana.contract.entity.DisputeState
import com.trana.contract.repository.ContractRepository
import com.trana.contract.service.ContractAccessGuard
import com.trana.contract.service.CounterpartyResolver
import com.trana.dispute.DisputeException
import com.trana.dispute.entity.DisputeRecord
import com.trana.dispute.entity.DisputeStatus
import com.trana.dispute.repository.DisputeRecordRepository
import com.trana.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 분쟁(신고) 서비스.
 *
 * - report: 신고 접수 (SIGNED/COMPLETED 만, 본인 활성 신고 1건 한정)
 * - cancelByReporter: 신고자 본인이 자기 신고 취소 (A-4c)
 *
 * 알림톡 발송 (피신고자) 은 별도 NOT_SUPPORTED 트랜잭션 — A-7 wire 시 추가.
 */
@Service
@Transactional
class DisputeService(
    private val accessGuard: ContractAccessGuard,
    private val disputeRecordRepository: DisputeRecordRepository,
    private val counterpartyResolver: CounterpartyResolver,
    private val userRepository: UserRepository,
    private val kakaoAlimtalkClient: KakaoAlimtalkClient,
    private val webUrlBuilder: WebUrlBuilder,
    private val contractRepository: ContractRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /**
     * 신고 접수.
     *
     * @param publicCode 신고 대상 계약
     * @param reporterUserId 신고자 user id
     * @param reason 신고 사유 (한 줄 요약)
     * @param detail 신고 상세 내용 (자유 텍스트)
     * @param reporterIp 신고 시점 IP (audit, nullable)
     * @return INSERT 된 DisputeRecord
     */
    fun report(
        publicCode: String,
        reporterUserId: Long,
        reason: String,
        detail: String,
        reporterIp: String?,
    ): DisputeRecord {
        val contract = accessGuard.loadAccessible(publicCode, reporterUserId)
        ensureReportable(contract)
        ensureNoActiveReportByReporter(contract, reporterUserId)

        val dispute =
            disputeRecordRepository.save(
                DisputeRecord(
                    contractId = contract.id!!,
                    reporterUserId = reporterUserId,
                    reason = reason,
                    detail = detail,
                    reporterIp = reporterIp,
                ),
            )

        if (contract.disputeState == DisputeState.NONE) {
            contract.markReported()
        }

        sendDisputeReportedAlimtalk(contract, reporterUserId, dispute)
        return dispute
    }

    /**
     * 신고자 본인이 자기 신고 취소.
     * 활성 신고가 0건이 되면 contract.disputeState 도 NONE 으로 복원.
     *
     * @param publicCode URL 의 계약 publicCode
     * @param disputeId 취소 대상 신고 id
     * @param userId 신고자 본인 id (JWT 인증)
     */
    fun cancelByReporter(
        publicCode: String,
        disputeId: Long,
        userId: Long,
    ) {
        val contract = accessGuard.loadAccessible(publicCode, userId)
        val dispute =
            disputeRecordRepository
                .findFirstByContractIdAndIdAndReporterUserIdAndStatus(
                    contract.id!!,
                    disputeId,
                    userId,
                    DisputeStatus.REPORTED,
                )
                ?: throw DisputeException.NotFound(disputeId)

        dispute.cancelByReporter()
        restoreContractIfNoActiveReport(contract, disputeId)
    }

    /**
     * 계약 단위 신고 목록 (양측 조회).
     */
    @Transactional(readOnly = true)
    fun list(
        publicCode: String,
        userId: Long,
    ): List<DisputeRecord> {
        val contract = accessGuard.loadAccessible(publicCode, userId)
        return disputeRecordRepository.findByContractIdOrderByReportedAtDesc(contract.id!!)
    }

    /**
     * 운영팀 사기 판정 — `markFraudConfirmed` 또는 `markFraudDismissed` + 이벤트 발행.
     *
     * @param disputeId 판정 대상 신고 ID
     * @param fraud true = 사기 확인 (FRAUD_CONFIRMED, 신고자 +5 / 신고 대상 -15) / false = 사기 아님 (FRAUD_DISMISSED, 점수 변동 X)
     * @param adminUserId 판정 운영자 ID (W7 RBAC 도입 전까지 dev endpoint = 임의 user)
     * @param reason 판정 사유 (자유 텍스트, audit 영구 보존)
     *
     * 호출:
     * - 임시 dev endpoint (W7 RBAC 전까지) — POST /v1/dev/disputes/{id}/resolve?fraud=...
     * - W7+ admin endpoint 로 교체
     */
    fun resolve(
        disputeId: Long,
        fraud: Boolean,
        adminUserId: Long,
        reason: String,
    ) {
        val dispute =
            disputeRecordRepository.findById(disputeId).orElseThrow {
                DisputeException.NotFound(disputeId)
            }
        val contract =
            contractRepository.findById(dispute.contractId).orElseThrow {
                IllegalStateException("계약 조회 실패 (contractId=${dispute.contractId})")
            }
        val reportedUserId =
            counterpartyResolver.resolveCounterpartUserId(contract, dispute.reporterUserId)
                ?: error("신고 대상 user 미상 (disputeId=$disputeId, reporterUserId=${dispute.reporterUserId})")

        if (fraud) {
            dispute.markFraudConfirmed(adminUserId, reason)
        } else {
            dispute.markFraudDismissed(adminUserId, reason)
        }

        eventPublisher.publishEvent(
            DisputeResolvedEvent(
                disputeId = dispute.id!!,
                contractId = contract.id!!,
                reporterUserId = dispute.reporterUserId,
                reportedUserId = reportedUserId,
                resolution = dispute.resolution,
                resolvedByAdminUserId = adminUserId,
                resolvedAt = dispute.resolvedAt!!,
            ),
        )
    }

    private fun restoreContractIfNoActiveReport(
        contract: Contract,
        cancelledDisputeId: Long,
    ) {
        val otherActiveCount =
            disputeRecordRepository.countByContractIdAndStatusAndIdNot(
                contract.id!!,
                DisputeStatus.REPORTED,
                cancelledDisputeId,
            )
        if (otherActiveCount == 0L && contract.disputeState == DisputeState.REPORTED) {
            contract.markReportCancelled()
        }
    }

    private fun ensureReportable(contract: Contract) {
        if (contract.status != ContractStatus.SIGNED && contract.status != ContractStatus.COMPLETED) {
            throw DisputeException.NotReportable(contract.publicCode, contract.status.name)
        }
    }

    private fun ensureNoActiveReportByReporter(
        contract: Contract,
        reporterUserId: Long,
    ) {
        if (disputeRecordRepository.existsByContractIdAndReporterUserIdAndStatus(
                contract.id!!,
                reporterUserId,
                DisputeStatus.REPORTED,
            )
        ) {
            throw DisputeException.AlreadyActive(contract.publicCode, reporterUserId)
        }
    }

    private val log = LoggerFactory.getLogger(javaClass)

    private fun sendDisputeReportedAlimtalk(
        contract: Contract,
        reporterUserId: Long,
        record: DisputeRecord,
    ) {
        val recipientId = counterpartyResolver.resolveCounterpartUserId(contract, reporterUserId)
        if (recipientId == null) {
            log.warn(
                "[DISPUTE] 피신고자 미상 — 알림톡 skip (publicCode={}, reporterUserId={})",
                contract.publicCode,
                reporterUserId,
            )
            return
        }
        val recipient = userRepository.findById(recipientId).orElse(null)
        if (recipient?.name == null || recipient.phone == null) {
            log.warn(
                "[DISPUTE] 피신고자 정보 불완전 — 알림톡 skip (publicCode={}, recipientId={})",
                contract.publicCode,
                recipientId,
            )
            return
        }
        kakaoAlimtalkClient.sendDisputeReported(
            DisputeReportedMessage(
                recipientName = recipient.name!!,
                recipientPhone = recipient.phone!!,
                contractTitle = contract.title ?: "",
                reportedAt = record.reportedAt!!,
                detailUrl = webUrlBuilder.contractDetail(contract.publicCode),
            ),
        )
    }
}
