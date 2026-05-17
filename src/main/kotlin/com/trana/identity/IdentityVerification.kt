package com.trana.identity

import com.trana.identity.adapter.FaceCompareResult
import com.trana.identity.adapter.Gender
import com.trana.identity.adapter.IdCardVerifyResult
import com.trana.identity.adapter.IdType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * KYC 시도/결과 영구 기록.
 *
 * - 단계별 누적 update:
 *   1) OCR: PENDING record 생성 (identifier_hash, name, birth_date 등 OCR 시점 정보)
 *   2) Verify: verify_passed update. 실패 시 FAILED + failure_step=VERIFY
 *   3) Compare: face_similarity/face_match update. 마지막 단계 → SUCCESS/FAILED 최종
 * - id_card_verify_sessions(임시 10분)와 별개 — 영구 보관
 */
@Entity
@Table(name = "identity_verifications")
@Suppress("LongParameterList")
class IdentityVerification(
    @Column(name = "user_id")
    val userId: Long? = null,
    @Column(name = "signup_session_id")
    val signupSessionId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "id_type", nullable = false, length = 30)
    val idType: IdType,
    @Column(name = "ncp_document_request_id", nullable = false, length = 100)
    val ncpDocumentRequestId: String,
    @Column(name = "identifier_hash", nullable = false, length = 64)
    val identifierHash: String,
    @Column(name = "name", length = 100)
    val name: String? = null,
    @Column(name = "birth_date")
    val birthDate: LocalDate? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    val gender: Gender? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: IdentityVerificationStatus = IdentityVerificationStatus.PENDING,
    @Column(name = "verify_passed", nullable = false)
    var verifyPassed: Boolean = false,
    @Column(name = "verify_error_code", length = 50)
    var verifyErrorCode: String? = null,
    @Column(name = "verify_error_message", columnDefinition = "TEXT")
    var verifyErrorMessage: String? = null,
    @Column(name = "face_similarity")
    var faceSimilarity: Double? = null,
    @Column(name = "face_match")
    var faceMatch: Boolean? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_step", length = 30)
    var failureStep: FailureStep? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime? = null

    /** Verify API 결과 적용. 이미 final이면 무시 (한 record = 한 시도). */
    fun applyVerify(result: IdCardVerifyResult) {
        if (status != IdentityVerificationStatus.PENDING) return
        verifyPassed = result.isValid
        verifyErrorCode = result.errorCode
        verifyErrorMessage = result.errorMessage
        if (!result.isValid) {
            status = IdentityVerificationStatus.FAILED
            failureStep = FailureStep.VERIFY
        }
    }

    /** Compare API 결과 적용. 이미 final이면 무시. */
    fun applyCompare(result: FaceCompareResult) {
        if (status != IdentityVerificationStatus.PENDING) return
        faceSimilarity = result.similarity
        faceMatch = result.isMatch
        if (result.isMatch) {
            status = IdentityVerificationStatus.SUCCESS
        } else {
            status = IdentityVerificationStatus.FAILED
            failureStep = FailureStep.COMPARE
        }
    }
}

enum class IdentityVerificationStatus { PENDING, SUCCESS, FAILED }

enum class FailureStep { OCR, VERIFY, COMPARE }
