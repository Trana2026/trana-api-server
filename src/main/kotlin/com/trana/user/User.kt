package com.trana.user

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
import java.time.LocalDate

@Entity
@Table(name = "users")
class User(
    @Column(name = "public_code", nullable = false, unique = true, length = 20)
    val publicCode: String,
    @Column(unique = true, length = 255)
    var email: String? = null,
    @Column(length = 50)
    var nickname: String? = null,
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

    fun changeNickname(newNickname: String) {
        require(newNickname.length in NICKNAME_MIN_LENGTH..NICKNAME_MAX_LENGTH) {
            "닉네임은 ${NICKNAME_MIN_LENGTH}~${NICKNAME_MAX_LENGTH}자여야 합니다"
        }
        this.nickname = newNickname
    }

    fun withdraw() {
        check(status == UserStatus.ACTIVE) { "이미 탈퇴한 사용자입니다" }
        this.status = UserStatus.WITHDRAWN
        this.withdrawnAt = Instant.now()
    }

    fun markGuardianVerified() {
        check(ageGroup == AgeGroup.MINOR) { "보호자 인증은 미성년자만 가능합니다" }
        check(guardianVerifiedAt == null) { "이미 보호자 인증된 사용자입니다" }
        this.guardianVerifiedAt = Instant.now()
    }

    fun applyKycResult(
        name: String,
        birthDate: LocalDate,
        gender: Gender,
        ageGroup: AgeGroup,
        phone: String? = null,
    ) {
        check(this.ageGroup == null) { "이미 KYC 완료된 사용자입니다" }
        this.name = name
        this.birthDate = birthDate.toString() // LocalDate → "yyyy-MM-dd"
        this.gender = gender
        this.ageGroup = ageGroup
        phone?.let { this.phone = it }
    }

    companion object {
        private const val NICKNAME_MIN_LENGTH = 2
        private const val NICKNAME_MAX_LENGTH = 20
    }
}

enum class UserStatus {
    ACTIVE,
    WITHDRAWN,
}

enum class AgeGroup {
    ADULT,
    MINOR,
}

enum class Gender {
    MALE,
    FEMALE,
    OTHER,
}
