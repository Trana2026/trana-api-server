package com.trana.notification.service

import com.trana.common.response.PageResponse
import com.trana.notification.NotificationException
import com.trana.notification.dto.NotificationSummaryResponse
import com.trana.notification.dto.toResponse
import com.trana.notification.repository.NotificationRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 앱 알림함 서비스 — 목록/읽음/삭제.
 *
 * 정렬은 서버 강제 (createdAt DESC) — 클라이언트 sort 파라미터 X.
 * 본인 소유 검증은 Repository.findByIdAndUserId 로 SQL WHERE 결합 (id 만 조회 후 후검증 X).
 */
@Service
@Transactional
class NotificationService(
    private val notificationRepository: NotificationRepository,
) {
    @Transactional(readOnly = true)
    fun list(
        userId: Long,
        page: Int,
        size: Int,
    ): PageResponse<NotificationSummaryResponse> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = notificationRepository.findAllByUserId(userId, pageable)
        return PageResponse.from(result) { it.toResponse() }
    }

    /** 읽음 처리 (idempotent — Entity.markRead 가 이미 읽음이면 no-op). */
    fun markRead(
        userId: Long,
        notificationId: Long,
    ) {
        val notification =
            notificationRepository.findByIdAndUserId(notificationId, userId)
                ?: throw NotificationException.NotFound(notificationId)
        notification.markRead()
    }

    /** hard delete — audit 성격 아님. */
    fun delete(
        userId: Long,
        notificationId: Long,
    ) {
        val notification =
            notificationRepository.findByIdAndUserId(notificationId, userId)
                ?: throw NotificationException.NotFound(notificationId)
        notificationRepository.delete(notification)
    }
}
