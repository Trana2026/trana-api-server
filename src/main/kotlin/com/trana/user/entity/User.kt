package com.trana.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

/**
 * 사용자 — 성인/미성년자 통합 Entity.
 *
 * - 성인 가입: 본인 KYC SUCCESS 시점에 INSERT (publicCode + name/birthDate/gender/phone + ageGroup=ADULT)
 * - 미성년자 가입: 소셜 로그인 시점에 INSERT (publicCode + email/name + ageGroup=MINOR)
 *   → 보호자 KYC SUCCESS 시 markGuardianVerified() 호출 = 가입 완료
 *
 * 가입 완료 판정:
 * - ADULT: ageGroup == ADULT (INSERT 시점에 이미 완료)
 * - MINOR: guardianVerifiedAt != null
 */
@Entity
@Table(name = "users")
class User(
    @Column(name = "public_code", nullable = false, unique = true, length = 20)
    val publicCode: String,
    @Column(unique = true, length = 255)
    var email: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "age_group", length = 10)
    var ageGroup: AgeGroup? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: UserStatus = UserStatus.ACTIVE,
    @Column(length = 255)
    var name: String? = null,
    @Column(name = "birth_date", length = 50)
    var birthDate: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    var gender: Gender? = null,
    @Column(length = 255)
    var phone: String? = null,
    @Column(name = "push_enabled", nullable = false)
    var pushEnabled: Boolean = true,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
        protected set

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
        protected set

    @Column(name = "withdrawn_at")
    var withdrawnAt: Instant? = null
        protected set

    @Column(name = "guardian_verified_at")
    var guardianVerifiedAt: Instant? = null
        protected set

    fun withdraw() {
        check(status == UserStatus.ACTIVE) { "이미 탈퇴한 사용자입니다" }
        this.status = UserStatus.WITHDRAWN
        this.withdrawnAt = Instant.now()
        // PII 마스킹 — 재가입 시 email unique 충돌 회피 + 개인정보 최소화
        // name/phone(성인 KYC)은 audit 가치라 W7+ 운영 정교화 시 결정
        this.email = null
    }

    fun markGuardianVerified() {
        check(ageGroup == AgeGroup.MINOR) { "보호자 인증은 미성년자만 가능" }
        check(guardianVerifiedAt == null) { "이미 보호자 인증된 사용자" }
        this.guardianVerifiedAt = Instant.now()
    }

    companion object {
        private const val NICKNAME_MIN_LENGTH = 2
        private const val NICKNAME_MAX_LENGTH = 20
    }
}

enum class UserStatus { ACTIVE, WITHDRAWN }

enum class AgeGroup { ADULT, MINOR }

enum class Gender { MALE, FEMALE, OTHER }
