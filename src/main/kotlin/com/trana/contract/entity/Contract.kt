package com.trana.contract.entity

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
 * 전자계약 본문 (Aggregate root).
 *
 * - 작성/수정/삭제 권한: creatorUserId 만 (DRAFT 단계)
 * - 상태: status × disputeState 직교 모델
 * - 미성년 분기: consentType + guardianConsentAt
 *
 * 흐름 (W4):
 * - createDraft → DRAFT 빈 row 생성 (delivery/consent 만 결정)
 * - updateDraft → 사진/AI 추출 후 필드 채움
 * - softDelete → DRAFT 만 가능
 *
 * 불변식:
 * - DRAFT 에서만 수정/삭제
 * - GUARDIAN_REQUIRED 인데 guardianConsentAt=null 이면 SIGN_REQUESTED 전이 금지 (W5)
 * - SIGN_REQUESTED 진입 후 본문 변경 불가 (W5)
 */
@Entity
@Table(name = "contracts")
@Suppress("LongParameterList")
class Contract(
    @Column(name = "public_code", nullable = false, unique = true, length = 20)
    val publicCode: String,
    @Column(name = "creator_user_id", nullable = false)
    val creatorUserId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", nullable = false, length = 20)
    var deliveryType: DeliveryType,
    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 30)
    var consentType: ConsentType,
    @Column(name = "title", length = 200)
    var title: String? = null,
    @Column(name = "price")
    var price: Long? = null,
    @Column(name = "condition_summary", columnDefinition = "text")
    var conditionSummary: String? = null,
    @Column(name = "condition_details", columnDefinition = "text")
    var conditionDetails: String? = null,
    @Column(name = "warranty_period_days", nullable = false)
    var warrantyPeriodDays: Int = WARRANTY_DEFAULT_DAYS,
    @Column(name = "location", length = 100)
    var location: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    var status: ContractStatus = ContractStatus.DRAFT
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "dispute_state", nullable = false, length = 20)
    var disputeState: DisputeState = DisputeState.NONE
        protected set

    @Column(name = "guardian_id")
    var guardianId: Long? = null
        protected set

    @Column(name = "guardian_consent_at")
    var guardianConsentAt: Instant? = null
        protected set

    @Column(name = "version", nullable = false)
    var version: Int = 1
        protected set

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
        protected set

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
        protected set

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
        protected set

    fun updateDraft(
        title: String? = null,
        price: Long? = null,
        conditionSummary: String? = null,
        conditionDetails: String? = null,
        location: String? = null,
        deliveryType: DeliveryType? = null,
    ) {
        check(status == ContractStatus.DRAFT) { "DRAFT 상태에서만 수정 가능" }
        check(deletedAt == null) { "삭제된 계약은 수정 불가" }
        title?.let { this.title = it }
        price?.let { this.price = it }
        conditionSummary?.let { this.conditionSummary = it }
        conditionDetails?.let { this.conditionDetails = it }
        location?.let { this.location = it }
        deliveryType?.let { this.deliveryType = it }
    }

    fun markGuardianConsented(boundGuardianId: Long) {
        check(consentType == ConsentType.GUARDIAN_REQUIRED) {
            "GUARDIAN_REQUIRED 계약만 보호자 동의 가능"
        }
        check(guardianConsentAt == null) { "이미 보호자 동의 완료된 계약" }
        this.guardianId = boundGuardianId
        this.guardianConsentAt = Instant.now()
    }

    fun softDelete() {
        check(status == ContractStatus.DRAFT) { "DRAFT 상태에서만 삭제 가능" }
        check(deletedAt == null) { "이미 삭제된 계약" }
        this.deletedAt = Instant.now()
    }

    companion object {
        private const val WARRANTY_DEFAULT_DAYS = 3

        fun createDraft(
            publicCode: String,
            creatorUserId: Long,
            deliveryType: DeliveryType,
            consentType: ConsentType,
        ): Contract =
            Contract(
                publicCode = publicCode,
                creatorUserId = creatorUserId,
                deliveryType = deliveryType,
                consentType = consentType,
            )
    }
}

enum class ContractStatus { DRAFT, SIGN_REQUESTED, REVISION_REQUESTED, SIGNED, COMPLETED }

enum class DisputeState { NONE, REPORTED, RESOLVED, DISMISSED }

enum class DeliveryType { DIRECT, SHIPPING }

enum class ConsentType { GUARDIAN_REQUIRED, NONE, NOT_APPLICABLE }
