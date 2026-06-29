package com.trana.guardian.entity

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
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.time.LocalDate

/**
 * 보호자 마스터.
 *
 * - identifier_hash 기준 unique → 같은 보호자가 여러 미성년자 인증 시 같은 row 재사용
 * - upsert는 Service에서 findByIdentifierHash + create
 * - 미성년자↔보호자 매칭은 identity_verifications.subject_user_id / guardian_id 로 도출
 * - KYC 흐름(생성 트리거)은 Phase 6에서 identity 도메인이 호출
 */
@Entity
@Table(name = "guardians")
class Guardian(
    @Column(name = "identifier_hash", unique = true, length = 64)
    val identifierHash: String? = null,
    @Column(name = "ci_hash", length = 64)
    val ciHash: String? = null,
    @Column(name = "name", nullable = false, length = 100)
    val name: String,
    @Column(name = "birth_date", nullable = false)
    val birthDate: LocalDate,
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 10)
    val gender: Gender,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant? = null
}
