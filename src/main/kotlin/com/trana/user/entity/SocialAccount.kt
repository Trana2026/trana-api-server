package com.trana.user.entity

import com.trana.auth.oauth.SocialProvider
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
 * 소셜 계정 매핑. 미성년자 가입 시만 INSERT (성인은 소셜 X).
 *
 * 한 user가 여러 provider 연결 가능 (UNIQUE는 provider + providerUserId).
 */
@Entity
@Table(name = "social_accounts")
class SocialAccount(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val provider: SocialProvider,
    @Column(name = "provider_user_id", nullable = false, length = 255)
    val providerUserId: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null
}
