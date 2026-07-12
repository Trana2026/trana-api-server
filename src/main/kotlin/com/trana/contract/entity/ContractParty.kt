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
import java.time.Instant

/**
 * 계약 당사자 (SELLER 또는 BUYER).
 *
 * - W4: creator 만 INSERT (역할 = SELLER 또는 BUYER, 사용자가 [1] 단계에서 선택)
 * - W5: invitation 매핑 시 상대편 INSERT
 *
 * 불변식:
 * - (contract_id, party_type) UNIQUE — 한 계약에 같은 역할 중복 불가
 * - markValidated() 는 KYC SUCCESS + ACTIVE user 확인 후만 호출 (Service 책임)
 * - markCompleted() 는 멱등 X (이미 완료면 fail) — 양측 각자 1회만
 */
@Entity
@Table(name = "contract_parties")
class ContractParty(
    @Column(name = "contract_id", nullable = false)
    val contractId: Long,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false, length = 20)
    val partyType: PartyType,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(name = "validated", nullable = false)
    var validated: Boolean = false
        protected set

    @Column(name = "validated_at")
    var validatedAt: Instant? = null
        protected set

    @Column(name = "completed_at")
    var completedAt: Instant? = null
        protected set

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    val joinedAt: Instant? = null

    fun markValidated() {
        check(!validated) { "이미 검증된 당사자" }
        this.validated = true
        this.validatedAt = Instant.now()
    }

    fun markCompleted() {
        check(completedAt == null) { "이미 거래 완료를 클릭한 당사자" }
        this.completedAt = Instant.now()
    }

    companion object {
        fun create(
            contractId: Long,
            userId: Long,
            partyType: PartyType,
        ): ContractParty =
            ContractParty(
                contractId = contractId,
                userId = userId,
                partyType = partyType,
            )
    }
}

enum class PartyType { SELLER, BUYER }
