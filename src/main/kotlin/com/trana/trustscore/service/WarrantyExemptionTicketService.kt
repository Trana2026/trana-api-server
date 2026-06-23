package com.trana.trustscore.service

import com.trana.notification.service.NotificationDispatchService
import com.trana.trustscore.TrustScoreException
import com.trana.trustscore.entity.IssueReason
import com.trana.trustscore.entity.TicketStatus
import com.trana.trustscore.entity.WarrantyExemptionTicket
import com.trana.trustscore.repository.WarrantyExemptionTicketRepository
import com.trana.user.entity.TrustGrade
import com.trana.user.entity.User
import com.trana.user.entity.UserStatus
import com.trana.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 면제 티켓 도메인 서비스.
 *
 * - issueMonthlyBatch : 매월 1일 cron — 신뢰/우수 등급 user 자동 발급 (같은 달 중복 차단)
 * - expireBatch : 매일 cron — expires_at 경과 + UNUSED → EXPIRED 마킹
 * - notifyExpiringSoonBatch : 매일 cron — 만료 3일 전 + 미알림 → FCM 발송 + expiry_notified_at 마킹
 * - countUnusedTickets : 본인 보유 티켓 수 조회 (마이페이지 응답 — Phase 6 RiskSignals 와 같이 노출)
 *
 * useTicket(userId, contractId) 는 Phase 6 (결제 endpoint 진입 시) 추가 — 결제 시스템 X 라 보류.
 */
@Service
@Transactional
class WarrantyExemptionTicketService(
    private val ticketRepository: WarrantyExemptionTicketRepository,
    private val userRepository: UserRepository,
    private val notificationDispatchService: NotificationDispatchService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 매월 1일 cron 호출 — 신뢰 등급 + 우수 등급 user 일괄 발급.
     * 같은 달 + 같은 사유 중복 발급 차단 (idempotent).
     */
    fun issueMonthlyBatch(): IssueBatchResult {
        val now = Instant.now()
        val monthStart = startOfCurrentMonth(now)
        val monthEnd = monthStart.plus(MONTH_END_OFFSET_DAYS, ChronoUnit.DAYS)
        val expiresAt = now.plus(TICKET_VALIDITY_DAYS, ChronoUnit.DAYS)

        val trustUsers =
            userRepository.findAllByStatusAndTrustScoreBetween(
                UserStatus.ACTIVE,
                TrustGrade.TRUST.minScore,
                TrustGrade.TRUST.maxScore,
            )
        val excellentUsers =
            userRepository.findAllByStatusAndTrustScoreBetween(
                UserStatus.ACTIVE,
                TrustGrade.EXCELLENT.minScore,
                TrustGrade.EXCELLENT.maxScore,
            )

        var trustIssued = 0
        var excellentIssued = 0

        trustUsers.forEach { user ->
            if (issueIfNotAlreadyThisMonth(
                    user,
                    IssueReason.GRADE_TRUST_MONTHLY,
                    monthStart,
                    monthEnd,
                    expiresAt,
                    TRUST_MONTHLY_COUNT,
                )
            ) {
                trustIssued += TRUST_MONTHLY_COUNT
            }
        }
        excellentUsers.forEach { user ->
            if (issueIfNotAlreadyThisMonth(
                    user,
                    IssueReason.GRADE_EXCELLENT_MONTHLY,
                    monthStart,
                    monthEnd,
                    expiresAt,
                    EXCELLENT_MONTHLY_COUNT,
                )
            ) {
                excellentIssued += EXCELLENT_MONTHLY_COUNT
            }
        }

        log.info("[Ticket] issueMonthlyBatch — trust={}, excellent={}", trustIssued, excellentIssued)
        return IssueBatchResult(trustIssued = trustIssued, excellentIssued = excellentIssued)
    }

    /** 매일 cron 호출 — 만료 대상 UNUSED → EXPIRED 마킹. */
    fun expireBatch(): Int {
        val expired = ticketRepository.findAllByStatusAndExpiresAtBefore(TicketStatus.UNUSED, Instant.now())
        expired.forEach { it.markExpired() }
        log.info("[Ticket] expireBatch — expiredCount={}", expired.size)
        return expired.size
    }

    /** 매일 cron 호출 — 만료 3일 전 + 미알림 → FCM + expiry_notified_at 마킹. */
    fun notifyExpiringSoonBatch(): Int {
        val threshold = Instant.now().plus(EXPIRY_NOTICE_DAYS, ChronoUnit.DAYS)
        val targets =
            ticketRepository.findAllByStatusAndExpiresAtBeforeAndExpiryNotifiedAtIsNull(
                TicketStatus.UNUSED,
                threshold,
            )
        targets.forEach { ticket ->
            notificationDispatchService.sendToUser(
                userId = ticket.userId,
                title = "면제 티켓 만료 임박",
                body = "면제 티켓이 3일 후 만료돼요. 지금 계약을 만들어 사용하세요.",
                data =
                    mapOf(
                        "type" to "TICKET_EXPIRING",
                        "ticketId" to ticket.id.toString(),
                    ),
            )
            ticket.markExpiryNotified()
        }
        log.info("[Ticket] notifyExpiringSoonBatch — notifiedCount={}", targets.size)
        return targets.size
    }

    /** 본인 보유 UNUSED 티켓 수 — 마이페이지 / RiskSignals 응답용. */
    @Transactional(readOnly = true)
    fun countUnusedTickets(userId: Long): Long = ticketRepository.countByUserIdAndStatus(userId, TicketStatus.UNUSED)

    /**
     * 면제 티켓 1장 사용 (UNUSED → USED). FIFO — 가장 빠른 expires_at 우선.
     *
     * 호출:
     * - (W10+) 결제 endpoint — 계약 생성 수수료 결제 직전 사용자가 "면제 티켓 사용하기" 탭
     * - 결제 시스템 X — 일단 service 메서드 + dev endpoint 만 (실 결제 통합은 결제 도메인 도입 시)
     *
     * @throws TrustScoreException.NoUnusedTicket 보유 UNUSED 티켓이 없음 (명세 E03)
     */
    fun useTicket(
        userId: Long,
        contractId: Long,
    ): WarrantyExemptionTicket {
        val ticket =
            ticketRepository.findFirstByUserIdAndStatusOrderByExpiresAtAsc(
                userId,
                TicketStatus.UNUSED,
            )
                ?: throw TrustScoreException.NoUnusedTicket(userId)
        ticket.markUsed(contractId)
        return ticket
    }

    private fun issueIfNotAlreadyThisMonth(
        user: User,
        reason: IssueReason,
        monthStart: Instant,
        monthEnd: Instant,
        expiresAt: Instant,
        count: Int,
    ): Boolean {
        val userId = requireNotNull(user.id)
        if (ticketRepository.existsByUserIdAndIssueReasonAndIssuedAtBetween(
                userId,
                reason,
                monthStart,
                monthEnd,
            )
        ) {
            return false
        }
        repeat(count) {
            ticketRepository.save(
                WarrantyExemptionTicket(
                    userId = userId,
                    issueReason = reason,
                    expiresAt = expiresAt,
                ),
            )
        }
        return true
    }

    private fun startOfCurrentMonth(now: Instant): Instant {
        val today = LocalDate.ofInstant(now, KST)
        return today.withDayOfMonth(1).atStartOfDay(KST).toInstant()
    }

    companion object {
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
        private const val TICKET_VALIDITY_DAYS = 30L
        private const val EXPIRY_NOTICE_DAYS = 3L
        private const val MONTH_END_OFFSET_DAYS = 31L
        private const val TRUST_MONTHLY_COUNT = 1
        private const val EXCELLENT_MONTHLY_COUNT = 3
    }
}

data class IssueBatchResult(
    val trustIssued: Int,
    val excellentIssued: Int,
)
