package com.trana.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

/**
 * 사용자 차단 — 단방향(blocker → blocked). insert-only(상태 없음).
 *
 * - 차단당한 사용자(blocked)가 생성한 계약을 차단한 사용자(blocker) 화면에서 숨긴다.
 * - 양측 서명 완료(SIGNED/COMPLETED) 계약은 예외로 항상 노출 → 계약 도메인 필터에서 처리.
 * - 해제는 row DELETE (unblock). 재차단은 (blocker, blocked) UNIQUE 로 멱등.
 */
@Entity
@Table(name = "user_blocks")
class UserBlock(
    @Column(name = "blocker_user_id", nullable = false)
    val blockerUserId: Long,
    @Column(name = "blocked_user_id", nullable = false)
    val blockedUserId: Long,
    @Column(length = 50)
    val reason: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null
}
