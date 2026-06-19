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
 * 1:1 문의 (단방향) — 사용자 → 운영자.
 *
 * - 작성 시점에 Slack webhook 발송 + DB INSERT
 * - 운영자 답변은 사용자 입력 email 로 직접 회신 (DB 저장 X)
 * - publicCode 12자 nanoid (Flutter 가 상세 조회용)
 * - email 필수 (성인 KYC 가입자는 user.email 없음 — 입력값 사용)
 */
@Entity
@Table(name = "user_inquiries")
class UserInquiry(
    @Column(name = "public_code", nullable = false, unique = true, length = 20)
    val publicCode: String,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(nullable = false, length = 255)
    val email: String,
    @Column(nullable = false, length = 100)
    val title: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null
}
