package com.trana.notification.repository

import com.trana.notification.entity.Notification
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationRepository : JpaRepository<Notification, Long> {
    /**
     * 알림 목록 페이징 조회. 정렬(createdAt DESC)은 Service 에서 Pageable 로 강제 —
     * Repository 메서드명에 정렬 넣으면 클라이언트 sort 파라미터 우회 여지가 생겨 분리.
     */
    fun findAllByUserId(
        userId: Long,
        pageable: Pageable,
    ): Page<Notification>

    /**
     * 본인 소유 검증 겸 상세 조회.
     * PATCH /read, DELETE 진입 시 사용 — id 만 매칭하면 다른 user 의 알림도 접근 가능하므로 userId 결합 필수.
     * null 반환 시 Service 에서 NotFound 로 변환 (존재 여부 노출 방지 — Forbidden 대신 404 통일).
     */
    fun findByIdAndUserId(
        id: Long,
        userId: Long,
    ): Notification?
}
