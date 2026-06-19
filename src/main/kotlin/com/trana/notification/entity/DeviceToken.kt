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
 * FCM 디바이스 토큰 (V13 device_tokens).
 *
 * - tokenEncrypted: AES-256-GCM 암호화 (랜덤 IV). LiveFcmClient.send 가 복호화 후 발송
 * - tokenHash: SHA-256 deterministic hex (64자). UNIQUE 제약 + 등록 시 매칭 키
 *
 * multi-device 지원 — 한 user 가 여러 row 보유.
 * 같은 단말이 다른 user 로 재로그인 시 token_hash 동일 → [reassignTo] 로 userId 갱신 (C-4 책임).
 * invalid 응답 시 (C-5) token_hash 로 row 삭제.
 * - lastUsedAt: 마지막 FCM 발송 성공 시각 — 마이페이지 기기 관리 UX. 등록 직후 null
 */
@Entity
@Table(name = "device_tokens")
class DeviceToken(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "token_encrypted", nullable = false, columnDefinition = "TEXT")
    val tokenEncrypted: String,
    @Column(name = "token_hash", nullable = false, length = 64)
    val tokenHash: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 16)
    val platform: DevicePlatform,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null

    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null
        protected set

    /** 같은 단말이 다른 user 로 재로그인 시 호출 — 기존 row 의 userId 갱신 (token_hash 는 동일). */
    fun reassignTo(newUserId: Long) {
        check(newUserId != userId) { "같은 user 로의 reassignTo 는 의미 없음" }
        this.userId = newUserId
    }

    /** Flutter 가 앱 foreground 진입 시 POST /v1/notifications/device-tokens/ping 호출. */
    fun markUsed() {
        this.lastUsedAt = Instant.now()
    }
}

enum class DevicePlatform {
    ANDROID,
    IOS,
}
