package com.trana.trustscore.repository

import com.trana.trustscore.entity.IssueReason
import com.trana.trustscore.entity.TicketStatus
import com.trana.trustscore.entity.WarrantyExemptionTicket
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface WarrantyExemptionTicketRepository : JpaRepository<WarrantyExemptionTicket, Long> {
    /** 사용자 보유 UNUSED 티켓 수 (마이페이지 응답 / 사용 가능 여부 판정). */
    fun countByUserIdAndStatus(
        userId: Long,
        status: TicketStatus,
    ): Long

    /** 사용자 UNUSED 티켓 1장 — 가장 빠른 expires_at 우선 사용 (FIFO). */
    fun findFirstByUserIdAndStatusOrderByExpiresAtAsc(
        userId: Long,
        status: TicketStatus,
    ): WarrantyExemptionTicket?

    /**
     * 같은 달 + 같은 사유 중복 발급 차단.
     * issue task 가 매월 1일 발급 — 같은 달에 이미 발급된 티켓이 있으면 skip (멱등).
     */
    fun existsByUserIdAndIssueReasonAndIssuedAtBetween(
        userId: Long,
        issueReason: IssueReason,
        from: Instant,
        to: Instant,
    ): Boolean

    /**
     * 만료 대상 — expires_at < now + UNUSED.
     * expire task 가 매일 실행해서 status = EXPIRED 마킹.
     */
    fun findAllByStatusAndExpiresAtBefore(
        status: TicketStatus,
        threshold: Instant,
    ): List<WarrantyExemptionTicket>

    /**
     * 만료 임박 + 미알림 — expires_at < now+3d + UNUSED + expiry_notified_at IS NULL.
     * expire task 가 매일 실행해서 FCM 1회 발송 + expiry_notified_at 채움.
     */
    fun findAllByStatusAndExpiresAtBeforeAndExpiryNotifiedAtIsNull(
        status: TicketStatus,
        threshold: Instant,
    ): List<WarrantyExemptionTicket>
}
