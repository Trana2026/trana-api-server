package com.trana.notification.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

/**
 * 앱 안 알림함 (V9 notifications).
 *
 * push 발송 여부와 무관 저장 — pushEnabled=false 여도 리스트 노출.
 * 실 push 트리거는 W10+ 도입 예정 (#139). 현재는 seed / dev 흐름.
 *
 * - readAt: null=미읽음, timestamp=읽음 시각 (별도 boolean 없이 read/unread 판정 + audit 정보 겸함)
 * - deepLink: Flutter 앱 라우팅 URL (예: "trana://contracts/{publicCode}"). null 이면 이동 X
 * - category: 코드 enum 으로만 제약, DB CHECK X → 카테고리 확장 유연
 * - audit 성격 아님 — hard delete 가능 (user 탈퇴 시 ON DELETE CASCADE)
 */
@Entity
@Table(name = "notifications")
class Notification(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    val category: NotificationCategory,
    @Column(name = "title", nullable = false, length = 200)
    val title: String,
    @Column(name = "body", nullable = false, length = 1000)
    val body: String,
    @Column(name = "deep_link", length = 500)
    val deepLink: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null

    @Column(name = "read_at")
    var readAt: Instant? = null
        protected set

    val isRead: Boolean get() = readAt != null

    /** 읽음 처리 — 이미 읽음이면 no-op (idempotent). PATCH /v1/notifications/{id}/read 진입점. */
    fun markRead() {
        if (readAt != null) return
        this.readAt = Instant.now()
    }
}

enum class NotificationCategory {
    CONTRACT,
}
