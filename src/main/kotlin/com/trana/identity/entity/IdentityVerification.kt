package com.trana.identity.entity

import com.trana.user.entity.Gender
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
import java.time.LocalDate
import java.util.UUID

/**
 * PASS 본인확인 시도/결과 영구 기록 (audit + 분쟁 증거 + 중복 가입 방지).
 *
 * - 본인 (purpose=SIGNUP): req-client-info 시 PENDING 생성, return SUCCESS 시 userId 백필
 * - 보호자 (purpose=GUARDIAN): subjectUserId(미성년자), guardianId, guardianLinkToken 사용
 *
 * status: PENDING (req-client-info) → SUCCESS (return 복호화 성공)
 */
@Entity
@Table(name = "identity_verifications")
@Suppress("LongParameterList")
class IdentityVerification(
    @Column(name = "client_tx_id", length = 40)
    val clientTxId: String? = null,
    @Column(name = "ci_hash", length = 64)
    var ciHash: String? = null,
    @Column(name = "user_id")
    var userId: Long? = null,
    @Column(name = "signup_session_id")
    val signupSessionId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: VerificationStatus = VerificationStatus.PENDING,
    @Column(name = "name", length = 100)
    var name: String? = null,
    @Column(name = "birth_date")
    var birthDate: LocalDate? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    var gender: Gender? = null,
    @Column(name = "phone", length = 255)
    var phone: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 20)
    val purpose: VerificationPurpose = VerificationPurpose.SIGNUP,
    @Column(name = "subject_user_id")
    val subjectUserId: Long? = null,
    @Column(name = "guardian_id")
    var guardianId: Long? = null,
    @Column(name = "guardian_link_token", length = 64)
    val guardianLinkToken: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null

    /**
     * PASS 본인확인 SUCCESS — return endpoint 에서 mobileOK 응답 복호화 후 호출.
     *
     * - PENDING + SIGNUP 상태에서만 가능
     * - PASS 결과 (name/birthDate/gender/phone) 백필 + ci_hash 저장
     * - userId 바인딩 + status SUCCESS 전이
     */
    fun markPassSuccess(
        ciHash: String,
        name: String,
        birthDate: LocalDate,
        gender: Gender,
        phone: String,
        boundUserId: Long,
    ) {
        check(status == VerificationStatus.PENDING) { "PENDING 상태에서만 markPassSuccess 가능" }
        check(purpose == VerificationPurpose.SIGNUP) { "본인 SIGNUP 만 markPassSuccess 가능" }
        this.ciHash = ciHash
        this.name = name
        this.birthDate = birthDate
        this.gender = gender
        this.phone = phone
        this.userId = boundUserId
        this.status = VerificationStatus.SUCCESS
    }

    /**
     * PASS 보호자 본인확인 SUCCESS — return endpoint 에서 GUARDIAN purpose 분기 시 호출.
     *
     * - PENDING + GUARDIAN 상태에서만 가능
     * - PASS 결과 백필 + ci_hash 저장 + guardianId 바인딩
     */
    fun markPassGuardianSuccess(
        ciHash: String,
        name: String,
        birthDate: LocalDate,
        gender: Gender,
        phone: String,
        boundGuardianId: Long,
    ) {
        check(status == VerificationStatus.PENDING) { "PENDING 상태에서만 markPassGuardianSuccess 가능" }
        check(purpose == VerificationPurpose.GUARDIAN) { "GUARDIAN purpose 만 markPassGuardianSuccess 가능" }
        this.ciHash = ciHash
        this.name = name
        this.birthDate = birthDate
        this.gender = gender
        this.phone = phone
        this.guardianId = boundGuardianId
        this.status = VerificationStatus.SUCCESS
    }

    companion object {
        /**
         * PASS 본인 흐름 시작 — clientTxId 발급 시점 (req-client-info endpoint).
         *
         * - 인적 정보 (name / birthDate / gender / phone) 는 return endpoint 에서 markPassSuccess 시점에 백필
         * - status PENDING 으로 시작
         */
        fun startPassSignup(
            signupSessionId: UUID,
            clientTxId: String,
        ): IdentityVerification =
            IdentityVerification(
                signupSessionId = signupSessionId,
                clientTxId = clientTxId,
                purpose = VerificationPurpose.SIGNUP,
            )

        /**
         * PASS 보호자 흐름 시작 — req-client-info endpoint (보호자용).
         *
         * - purpose = GUARDIAN
         * - clientTxId 발급, subjectUserId(미성년자) + guardianLinkToken 보관
         * - 인적 정보는 return endpoint 에서 markPassGuardianSuccess 시점 백필
         */
        fun startPassGuardian(
            subjectUserId: Long,
            guardianLinkToken: String,
            clientTxId: String,
        ): IdentityVerification =
            IdentityVerification(
                clientTxId = clientTxId,
                purpose = VerificationPurpose.GUARDIAN,
                subjectUserId = subjectUserId,
                guardianLinkToken = guardianLinkToken,
            )
    }
}

enum class VerificationStatus { PENDING, SUCCESS, FAILED }

enum class VerificationPurpose { SIGNUP, GUARDIAN }
