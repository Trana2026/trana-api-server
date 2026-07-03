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
    @Column(name = "ci_hash", length = 64)
    var ciHash: String? = null,
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
    @Column(name = "trust_score", nullable = false)
    var trustScore: Int = INITIAL_TRUST_SCORE
        protected set

    @Column(name = "completed_contract_count", nullable = false)
    var completedContractCount: Int = 0
        protected set

    @Column(name = "warranty_provided_count", nullable = false)
    var warrantyProvidedCount: Int = 0
        protected set

    @Column(name = "fraud_report_filed_count", nullable = false)
    var fraudReportFiledCount: Int = 0
        protected set

    @Column(name = "fraud_report_received_count", nullable = false)
    var fraudReportReceivedCount: Int = 0
        protected set

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

    /**
     * 사기 확인 사용자 탈퇴 시 추가 PII 마스킹.
     * 명세 부록 — 원본 개인정보 (이름/전화) 즉시 삭제 + SHA-256 hash 만 영구 보존 (fraud_user_hashes).
     * [withdraw] 호출 이후 조합 (UserService 가 사기 확인 시 분기).
     */
    fun maskFraudPii() {
        check(status == UserStatus.WITHDRAWN) { "탈퇴 후에만 호출 가능" }
        this.name = null
        this.phone = null
    }

    fun markGuardianVerified() {
        check(ageGroup == AgeGroup.MINOR) { "보호자 인증은 미성년자만 가능" }
        check(guardianVerifiedAt == null) { "이미 보호자 인증된 사용자" }
        this.guardianVerifiedAt = Instant.now()
    }

    /**
     * 보호자 검증 상태 되돌리기 (개발 전용).
     *
     * DevTokenController 의 /v1/dev/guardian/reset 에서 호출 —
     * 미성년 계정으로 보호자 PASS 반복 테스트 시 사용. production 로직에서는 호출 X.
     */
    fun resetGuardianVerification() {
        check(ageGroup == AgeGroup.MINOR) { "미성년자만 보호자 검증 리셋 가능" }
        this.guardianVerifiedAt = null
    }

    /** 마이페이지 푸시 토글 — PATCH /v1/users/me/push-enabled. */
    fun changePushEnabled(enabled: Boolean) {
        this.pushEnabled = enabled
    }

    /**
     * 신뢰 점수 변동. 0~100 clamp.
     * @return Pair(before, after) — 호출자가 trust_score_events 에 기록
     */
    fun applyTrustScoreDelta(delta: Int): Pair<Int, Int> {
        val before = this.trustScore
        val after = (before + delta).coerceIn(MIN_TRUST_SCORE, MAX_TRUST_SCORE)
        this.trustScore = after
        return before to after
    }

    fun incrementCompletedContractCount() {
        this.completedContractCount++
    }

    fun incrementWarrantyProvidedCount() {
        this.warrantyProvidedCount++
    }

    fun incrementFraudReportFiledCount() {
        this.fraudReportFiledCount++
    }

    fun incrementFraudReportReceivedCount() {
        this.fraudReportReceivedCount++
    }

    /** 점수 → 등급 계산 (SOT = trustScore, 등급은 derived). */
    val trustGrade: TrustGrade
        get() = TrustGrade.fromScore(trustScore)

    companion object {
        private const val NICKNAME_MIN_LENGTH = 2
        private const val NICKNAME_MAX_LENGTH = 20
        const val INITIAL_TRUST_SCORE = 35
        const val MIN_TRUST_SCORE = 0
        const val MAX_TRUST_SCORE = 100
    }
}

enum class UserStatus { ACTIVE, WITHDRAWN }

enum class AgeGroup { ADULT, MINOR }

enum class Gender { MALE, FEMALE, OTHER }

/**
 * 신뢰 점수 등급 (점수 → 등급 derived).
 *
 * - NEWBIE   (0~34)   : 기본 수수료
 * - NORMAL   (35~54)  : 기본 수수료 (가입 default)
 * - TRUST    (55~74)  : 면제 티켓 월 1회
 * - EXCELLENT(75~89)  : 면제 티켓 월 3회
 * - BEST     (90~100) : 건당 수수료 할인 + 무제한 면제 티켓 (row 발급 X)
 */
enum class TrustGrade(
    val minScore: Int,
    val maxScore: Int,
    val label: String,
) {
    NEWBIE(0, 34, "새내기"),
    NORMAL(35, 54, "일반"),
    TRUST(55, 74, "신뢰"),
    EXCELLENT(75, 89, "우수"),
    BEST(90, 100, "최우수"),
    ;

    companion object {
        fun fromScore(score: Int): TrustGrade = entries.first { score in it.minScore..it.maxScore }
    }
}
