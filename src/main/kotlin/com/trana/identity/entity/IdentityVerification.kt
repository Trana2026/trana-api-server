package com.trana.identity.entity

import com.trana.user.entity.AgeGroup
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
 * KYC 시도/결과 영구 기록 (audit + 분쟁 증거 + 중복 가입 방지).
 *
 * - 본인 KYC: purpose=SIGNUP — OCR 시 PENDING 생성, Compare SUCCESS 시 userId 백필
 * - 보호자 KYC: purpose=GUARDIAN (Phase 6) — subjectUserId(미성년자), guardianId, guardianLinkToken 사용
 *
 * status:
 * - PENDING (OCR 통과, Verify 또는 Compare 진행 중)
 * - SUCCESS (Compare 통과)
 * - FAILED (어느 단계든 실패 — failureStep 기록)
 */
@Entity
@Table(name = "identity_verifications")
@Suppress("LongParameterList")
class IdentityVerification(
    @Column(name = "id_type", length = 30)
    val idType: String? = null,
    @Column(name = "ncp_document_request_id", length = 100)
    val ncpDocumentRequestId: String? = null,
    @Column(name = "identifier_hash", length = 64)
    val identifierHash: String? = null,
    @Column(name = "client_tx_id", length = 40)
    val clientTxId: String? = null,
    @Column(name = "ci_hash", length = 64)
    var ciHash: String? = null,
    @Column(name = "verify_passed", nullable = false)
    var verifyPassed: Boolean = false,
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
    @Column(name = "verify_error_code", length = 50)
    var verifyErrorCode: String? = null,
    @Column(name = "verify_error_message", columnDefinition = "text")
    var verifyErrorMessage: String? = null,
    @Column(name = "face_similarity")
    var faceSimilarity: Double? = null,
    @Column(name = "face_match")
    var faceMatch: Boolean? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_step", length = 30)
    var failureStep: FailureStep? = null,
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

    fun markVerifyPassed() {
        check(status == VerificationStatus.PENDING) { "PENDING 상태에서만 verify 통과 가능" }
        this.verifyPassed = true
    }

    fun markVerifyFailed(
        errorCode: String,
        errorMessage: String,
    ) {
        check(status == VerificationStatus.PENDING) { "PENDING 상태에서만 verify 실패 처리 가능" }
        this.verifyPassed = false
        this.verifyErrorCode = errorCode
        this.verifyErrorMessage = errorMessage
        this.status = VerificationStatus.FAILED
        this.failureStep = FailureStep.VERIFY
    }

    fun recordPhone(phoneValue: String) {
        check(status == VerificationStatus.PENDING) { "PENDING 상태에서만 phone 기록 가능" }
        check(verifyPassed) { "Verify 통과 후에만 phone 기록 가능" }
        this.phone = phoneValue
    }

    fun markCompareSuccess(
        similarity: Double,
        boundUserId: Long,
    ) {
        check(status == VerificationStatus.PENDING) { "PENDING 상태에서만 compare 성공 처리 가능" }
        check(verifyPassed) { "Verify 통과 후에만 compare 가능" }
        this.faceSimilarity = similarity
        this.faceMatch = true
        this.userId = boundUserId
        this.status = VerificationStatus.SUCCESS
    }

    fun markGuardianCompareSuccess(
        similarity: Double,
        boundGuardianId: Long,
    ) {
        check(status == VerificationStatus.PENDING) { "PENDING 상태에서만 compare 성공 처리 가능" }
        check(verifyPassed) { "Verify 통과 후에만 compare 가능" }
        check(purpose == VerificationPurpose.GUARDIAN) { "GUARDIAN 인증만 이 메서드 사용" }
        this.faceSimilarity = similarity
        this.faceMatch = true
        this.guardianId = boundGuardianId
        this.status = VerificationStatus.SUCCESS
    }

    /**
     * PASS 본인확인 SUCCESS — return endpoint 에서 mobileOK 응답 복호화 후 호출.
     *
     * - PENDING + SIGNUP 상태에서만 가능 (NCP/Guardian 흐름은 별도 메서드)
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

    fun markCompareFailed(
        similarity: Double?,
        errorCode: String,
        errorMessage: String,
    ) {
        check(status == VerificationStatus.PENDING) { "PENDING 상태에서만 compare 실패 처리 가능" }
        this.faceSimilarity = similarity
        this.faceMatch = false
        this.verifyErrorCode = errorCode
        this.verifyErrorMessage = errorMessage
        this.status = VerificationStatus.FAILED
        this.failureStep = FailureStep.COMPARE
    }

    companion object {
        @Suppress("LongParameterList")
        fun startSignup(
            idType: String,
            ncpDocumentRequestId: String,
            identifierHash: String,
            signupSessionId: UUID,
            name: String,
            birthDate: LocalDate,
            gender: Gender,
        ): IdentityVerification =
            IdentityVerification(
                idType = idType,
                ncpDocumentRequestId = ncpDocumentRequestId,
                identifierHash = identifierHash,
                signupSessionId = signupSessionId,
                purpose = VerificationPurpose.SIGNUP,
                name = name,
                birthDate = birthDate,
                gender = gender,
            )

        @Suppress("LongParameterList")
        fun startGuardian(
            idType: String,
            ncpDocumentRequestId: String,
            identifierHash: String,
            subjectUserId: Long,
            guardianLinkToken: String,
            name: String,
            birthDate: LocalDate,
            gender: Gender,
        ): IdentityVerification =
            IdentityVerification(
                idType = idType,
                ncpDocumentRequestId = ncpDocumentRequestId,
                identifierHash = identifierHash,
                purpose = VerificationPurpose.GUARDIAN,
                subjectUserId = subjectUserId,
                guardianLinkToken = guardianLinkToken,
                name = name,
                birthDate = birthDate,
                gender = gender,
            )

        /**
         * PASS 흐름 시작 — clientTxId 발급 시점 (req-client-info endpoint).
         *
         * - NCP 필드 (idType / ncpDocumentRequestId / identifierHash) 모두 null
         * - 인적 정보 (name / birthDate / gender / phone) 는 PASS return endpoint 에서 markPassSuccess 시점에 백필
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
    }
}

enum class VerificationStatus { PENDING, SUCCESS, FAILED }

enum class VerificationPurpose { SIGNUP, GUARDIAN }

enum class FailureStep { OCR, VERIFY, COMPARE }

fun AgeGroup.requiresGuardian(): Boolean = this == AgeGroup.MINOR
