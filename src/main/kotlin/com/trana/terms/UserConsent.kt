package com.trana.terms

import com.trana.user.AgeGroup
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnTransformer
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_consents")
class UserConsent(
    @Column(name = "user_id")
    val userId: Long? = null,
    @Column(name = "terms_version_id", nullable = false)
    val termsVersionId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "context_type", nullable = false, length = 20)
    val contextType: ConsentContextType,
    @Column(name = "context_id")
    val contextId: Long? = null,
    @Column(name = "signup_session_id", columnDefinition = "uuid")
    val signupSessionId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "age_group", nullable = false, length = 10)
    val ageGroup: AgeGroup,
    @Column(name = "agreed_at", nullable = false)
    val agreedAt: Instant,
    @Column(columnDefinition = "inet", nullable = false)
    @ColumnTransformer(write = "?::inet")
    val ip: String,
    @Column(name = "user_agent")
    val userAgent: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}

enum class ConsentContextType {
    SIGNUP,
    CONTRACT,
    MARKETING,
    KYC,
}
