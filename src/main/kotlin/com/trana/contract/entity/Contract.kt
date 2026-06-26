package com.trana.contract.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
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
 * 흐름 (W4~W6):
 * - createDraft → IN_PROGRESS 빈 row 생성 (delivery/consent 만 결정)
 * - updateDraft → 사진/AI 추출 후 필드 채움.
 *   4 필드 (title/price/conditionSummary/conditionDetails) 완성 시 자동 DRAFT,
 *   하나라도 비면 자동 IN_PROGRESS
 * - markReady → DRAFT → READY 전이 (PDF v1 생성, W5)
 * - markShared → READY → SHARED 전이 (수신자에게 알림톡 발송, W6)
 * - applyReceiverWarranty → SHARED 단계에서 수신자(SELLER) 보증기간 PATCH (PDF v1' 재생성, W7+)
 * - markRevisionRequested → SHARED → REVISION_REQUESTED (수신자 수정 요청, W6)
 * - markRevertToDraft → READY 또는 REVISION_REQUESTED → DRAFT (PDF 폐기 + 수정 모드, W6)
 * - markReceiverSigned → SHARED → RECEIVER_SIGNED (PDF v2 갱신, 수신자 서명, W6)
 * - markSigned → RECEIVER_SIGNED → SIGNED (PDF v3 양측 박스 채움, 생성자 최종 서명, W6)
 * - markCompleted → SIGNED → COMPLETED 전이 (양측 각자 markCompleted() 후 자동 호출, W7)
 * - softDelete → IN_PROGRESS / DRAFT 만 가능
 *
 * 불변식:
 * - DRAFT 에서만 수정/삭제
 * - DRAFT ↔ READY 전이는 markReady / markRevertToDraft 만 (Service 가 사전 검증, Entity 가 defense-in-depth)
 * - markReady 는 pdfS3Key + sha256 필수 (READY 는 항상 PDF 존재)
 * - revert 시 pdfS3Key/contentHash/pdfGeneratedAt 클리어 (S3 옛 버전은 Versioning 보존)
 * - GUARDIAN_REQUIRED 인데 guardianConsentAt=null 이면 READY 진입 금지
 * - SHARED 진입 후 본문 변경 불가 (W6 — 공유 후 수정 불가 정책)
 */
@Entity
@Table(name = "contracts")
@Suppress("LongParameterList", "TooManyFunctions")
class Contract(
    @Column(name = "public_code", nullable = false, unique = true, length = 20)
    val publicCode: String,
    @Column(name = "creator_user_id", nullable = false)
    val creatorUserId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", nullable = false, length = 20)
    var deliveryType: DeliveryType? = null,
    @Column(name = "trading_platform", length = 50)
    var tradingPlatform: String? = null,
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
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    var status: ContractStatus = ContractStatus.IN_PROGRESS
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
    var version: Int = 0
        protected set

    @Version
    @Column(name = "optimistic_version", nullable = false)
    var optimisticVersion: Long = 0
        protected set

    @Column(name = "pdf_s3_key", length = 500)
    var pdfS3Key: String? = null
        protected set

    @Column(name = "content_hash", length = 64)
    var contentHash: String? = null
        protected set

    @Column(name = "pdf_generated_at")
    var pdfGeneratedAt: Instant? = null
        protected set

    @Column(name = "completed_at")
    var completedAt: Instant? = null
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
        tradingPlatform: String? = null,
        deliveryType: DeliveryType? = null,
        warrantyPeriodDays: Int? = null,
    ) {
        check(status == ContractStatus.IN_PROGRESS || status == ContractStatus.DRAFT) {
            "IN_PROGRESS / DRAFT 상태에서만 수정 가능 (current=$status)"
        }
        check(deletedAt == null) { "삭제된 계약은 수정 불가" }
        warrantyPeriodDays?.let {
            check(it >= 0) { "warrantyPeriodDays 는 0 이상 (0=미제공, 양수=제공 일수)" }
            this.warrantyPeriodDays = it
        }
        title?.let { this.title = it }
        price?.let { this.price = it }
        conditionSummary?.let { this.conditionSummary = it }
        conditionDetails?.let { this.conditionDetails = it }
        tradingPlatform?.let { this.tradingPlatform = it }
        deliveryType?.let { this.deliveryType = it }
        this.status = if (allRequiredFieldsFilled()) ContractStatus.DRAFT else ContractStatus.IN_PROGRESS
    }

    private fun allRequiredFieldsFilled(): Boolean =
        title != null &&
            price != null &&
            conditionSummary != null &&
            conditionDetails != null &&
            tradingPlatform != null

    fun markGuardianConsented(boundGuardianId: Long) {
        check(consentType == ConsentType.GUARDIAN_REQUIRED) {
            "GUARDIAN_REQUIRED 계약만 보호자 동의 가능"
        }
        check(guardianConsentAt == null) { "이미 보호자 동의 완료된 계약" }
        this.guardianId = boundGuardianId
        this.guardianConsentAt = Instant.now()
    }

    fun markReady(
        pdfS3Key: String,
        pdfSha256: String,
    ) {
        check(status == ContractStatus.DRAFT) {
            "DRAFT (4 필드 완성) 상태에서만 READY 전이 가능 (current=$status)"
        }
        check(deletedAt == null) { "삭제된 계약은 전이 불가" }
        check(title != null) { "title 미입력 — AI 추출 또는 수동 입력 필요" }
        check(price != null) { "price 미입력" }
        check(conditionSummary != null) { "conditionSummary 미입력" }
        check(conditionDetails != null) { "conditionDetails 미입력" }
        check(tradingPlatform != null) { "tradingPlatform 미입력 — AI 추출 또는 수동 입력 필요" }
        if (consentType == ConsentType.GUARDIAN_REQUIRED) {
            check(guardianConsentAt != null) { "GUARDIAN_REQUIRED 인데 보호자 동의 미완료" }
        }
        this.status = ContractStatus.READY
        this.pdfS3Key = pdfS3Key
        this.contentHash = pdfSha256
        this.pdfGeneratedAt = Instant.now()
        this.version += 1
    }

    fun markShared() {
        check(status == ContractStatus.READY) { "READY 상태에서만 SHARED 전이 가능 (current=$status)" }
        check(deletedAt == null) { "삭제된 계약은 공유 불가" }
        this.status = ContractStatus.SHARED
    }

    fun markRevisionRequested() {
        check(status == ContractStatus.SHARED) {
            "SHARED 상태에서만 수정 요청 가능 (current=$status)"
        }
        check(deletedAt == null) { "삭제된 계약은 전이 불가" }
        this.status = ContractStatus.REVISION_REQUESTED
    }

    fun markRevertToDraft() {
        check(status == ContractStatus.READY || status == ContractStatus.REVISION_REQUESTED) {
            "READY 또는 REVISION_REQUESTED 상태에서만 DRAFT 되돌리기 가능 (current=$status)"
        }
        check(deletedAt == null) { "삭제된 계약은 전이 불가" }
        this.status = ContractStatus.DRAFT
        this.pdfS3Key = null
        this.contentHash = null
        this.pdfGeneratedAt = null
    }

    /**
     * 수신자(SELLER) 가 SHARED 단계에서 보증기간 제공 여부 결정.
     * PDF v1 → v1' 재생성 (양측이 보는 PDF 일치 보장) + version += 1.
     * - days = 0 : 보증 미제공
     * - days > 0 : 보증 일수 (default 3)
     *
     * 권한 검증 (party.partyType == SELLER + party.userId == receiverUserId) 은 Service 책임.
     */
    fun applyReceiverWarranty(
        days: Int,
        pdfS3Key: String,
        pdfSha256: String,
    ) {
        check(status == ContractStatus.SHARED) {
            "SHARED 상태에서만 수신자 보증기간 변경 가능 (current=$status)"
        }
        check(deletedAt == null) { "삭제된 계약은 변경 불가" }
        check(days >= 0) { "warrantyPeriodDays 는 0 이상 (0=미제공, 양수=제공 일수)" }
        this.warrantyPeriodDays = days
        this.pdfS3Key = pdfS3Key
        this.contentHash = pdfSha256
        this.pdfGeneratedAt = Instant.now()
        this.version += 1
    }

    fun markReceiverSigned(
        pdfS3Key: String,
        pdfSha256: String,
    ) {
        check(status == ContractStatus.SHARED) {
            "SHARED 상태에서만 RECEIVER_SIGNED 전이 가능 (current=$status)"
        }
        check(deletedAt == null) { "삭제된 계약은 전이 불가" }
        this.status = ContractStatus.RECEIVER_SIGNED
        this.pdfS3Key = pdfS3Key
        this.contentHash = pdfSha256
        this.pdfGeneratedAt = Instant.now()
    }

    fun markSigned(
        pdfS3Key: String,
        pdfSha256: String,
    ) {
        check(status == ContractStatus.RECEIVER_SIGNED) {
            "RECEIVER_SIGNED 상태에서만 SIGNED 전이 가능 (current=$status)"
        }
        check(deletedAt == null) { "삭제된 계약은 전이 불가" }
        this.status = ContractStatus.SIGNED
        this.pdfS3Key = pdfS3Key
        this.contentHash = pdfSha256
        this.pdfGeneratedAt = Instant.now()
    }

    fun markCompleted() {
        check(status == ContractStatus.SIGNED) {
            "SIGNED 상태에서만 COMPLETED 전이 가능 (current=$status)"
        }
        check(deletedAt == null) { "삭제된 계약은 전이 불가" }
        this.status = ContractStatus.COMPLETED
        this.completedAt = Instant.now()
    }

    /**
     * 취소 요청 발신 → status CANCEL_REQUESTED 전이.
     * 가능 시점: SHARED (1차 서명 수신자 = receiver 측) 또는 RECEIVER_SIGNED (최종 서명 수신자 = creator 측).
     * 즉 "본인이 서명 요청을 받은 상태" — service 가 요청자 측 검증.
     */
    fun markCancelRequested() {
        check(status == ContractStatus.SHARED || status == ContractStatus.RECEIVER_SIGNED) {
            "SHARED 또는 RECEIVER_SIGNED 상태에서만 취소 요청 가능 (current=$status)"
        }
        check(deletedAt == null) { "삭제된 계약은 취소 요청 불가" }
        this.status = ContractStatus.CANCEL_REQUESTED
    }

    /**
     * 상대 측 취소 확정 → status CANCELLED 전이.
     * service 가 활성 취소 요청 row 존재 + confirmer != requester 검증 후 호출.
     */
    fun markCancelled() {
        check(status == ContractStatus.CANCEL_REQUESTED) {
            "CANCEL_REQUESTED 상태에서만 CANCELLED 전이 가능 (current=$status)"
        }
        check(deletedAt == null) { "삭제된 계약은 전이 불가" }
        this.status = ContractStatus.CANCELLED
    }

    /**
     * 신고 접수 → dispute_state REPORTED 전이.
     * 신고 가능 상태: SIGNED 또는 COMPLETED (양측 본인확인 + 수신자 의사확정 이후만).
     * 다중 신고 시 service 가 분기 — contract 전이는 첫 신고에서만 (이미 REPORTED 면 차단).
     */
    fun markReported() {
        check(status == ContractStatus.SIGNED || status == ContractStatus.COMPLETED) {
            "SIGNED 또는 COMPLETED 상태에서만 신고 가능 (current=$status)"
        }
        check(deletedAt == null) { "삭제된 계약은 신고 불가" }
        check(disputeState == DisputeState.NONE) {
            "이미 분쟁 상태 (current=$disputeState) — 다중 신고는 service 가 분기"
        }
        this.disputeState = DisputeState.REPORTED
    }

    /**
     * 신고자 본인이 자기 신고를 취소했고, 그 결과 활성 신고가 0건이 된 경우 dispute_state NONE 복원.
     * service 가 활성(REPORTED) dispute_records count == 0 검증 후 호출.
     */
    fun markReportCancelled() {
        check(disputeState == DisputeState.REPORTED) {
            "REPORTED 상태에서만 NONE 복원 가능 (current=$disputeState)"
        }
        this.disputeState = DisputeState.NONE
    }

    fun softDelete() {
        check(status == ContractStatus.IN_PROGRESS || status == ContractStatus.DRAFT) {
            "IN_PROGRESS / DRAFT 상태에서만 삭제 가능 (current=$status)"
        }
        check(deletedAt == null) { "이미 삭제된 계약" }
        this.deletedAt = Instant.now()
    }

    companion object {
        private const val WARRANTY_DEFAULT_DAYS = 3

        fun createDraft(
            publicCode: String,
            creatorUserId: Long,
            deliveryType: DeliveryType? = null,
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

enum class ContractStatus {
    IN_PROGRESS,
    DRAFT,
    READY,
    SHARED,
    REVISION_REQUESTED,
    RECEIVER_SIGNED,
    CANCEL_REQUESTED,
    SIGNED,
    COMPLETED,
    CANCELLED,
}

enum class DisputeState { NONE, REPORTED }

enum class DeliveryType { DIRECT, SHIPPING }

enum class ConsentType { GUARDIAN_REQUIRED, NONE, NOT_APPLICABLE }
