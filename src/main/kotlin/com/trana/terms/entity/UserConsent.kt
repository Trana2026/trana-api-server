package com.trana.terms.entity

import com.trana.user.entity.AgeGroup
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnTransformer
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

/**
 * 사용자 약관 동의 기록 (법적 증거).
 *
 * - 성인 가입: 생성 시 user_id=null + signup_session_id=UUID. Compare SUCCESS 시 assignUserId(userId)로 백필
 * - 미성년자/로그인 후: 생성 직후 assignUserId(userId) 호출 (signup_session_id=null)
 */
@Entity
@Table(name = "user_consents")
@Suppress("LongParameterList")
class UserConsent(
    @Column(name = "terms_version_id", nullable = false)
    val termsVersionId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "context_type", nullable = false, length = 20)
    val contextType: ConsentContextType,
    @Enumerated(EnumType.STRING)
    @Column(name = "age_group", nullable = false, length = 10)
    val ageGroup: AgeGroup,
    @ColumnTransformer(write = "?::inet")
    @Column(name = "ip", nullable = false, columnDefinition = "inet")
    val ip: String,
    @Column(name = "context_id")
    val contextId: Long? = null,
    @Column(name = "signup_session_id")
    val signupSessionId: UUID? = null,
    @Column(name = "guardian_link_token", length = 64)
    val guardianLinkToken: String? = null,
    @Column(name = "user_agent", columnDefinition = "TEXT")
    val userAgent: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(name = "user_id")
    var userId: Long? = null
        protected set

    @CreationTimestamp
    @Column(name = "agreed_at", nullable = false, updatable = false)
    val agreedAt: Instant? = null

    /** 인증된 사용자(미성년자) 동의 시점 또는 가입 완료(Compare SUCCESS) 시점에 호출. */
    fun assignUserId(userId: Long) {
        check(this.userId == null) { "이미 user_id가 채워진 record" }
        this.userId = userId
    }
}

enum class ConsentContextType {
    SIGNUP, // 성인 본인 가입
    GUARDIAN_CONSENT, // 보호자가 미성년자 대신 동의
    CONTRACT,
    MARKETING,
    KYC,
}
