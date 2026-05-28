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
 * 양측 전자서명 audit (WORM — insert-only).
 *
 * 서명 순서 (수신자 먼저, 생성자 나중):
 * - 수신자 (creatorRole 반대편): RECEIVER_SIGNED 전이 시점 INSERT
 * - 생성자 (creatorRole): SIGNED 전이 시점 INSERT
 *
 * 불변식:
 * - (contract_id, party_type) UNIQUE — 같은 party 중복 서명 불가
 * - 모든 필드 immutable (val) — audit 보존
 * - pdfVersionAtSign = 서명 시점 contracts.version 스냅샷 (어느 리비전에 서명했는지)
 */
@Entity
@Table(name = "contract_signatures")
class ContractSignature(
    @Column(name = "contract_id", nullable = false)
    val contractId: Long,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false, length = 20)
    val partyType: PartyType,
    @Column(name = "signature_data", nullable = false, columnDefinition = "text")
    val signatureData: String,
    @Column(name = "pdf_version_at_sign", nullable = false)
    val pdfVersionAtSign: Int,
    @Column(name = "signer_ip", length = 45)
    val signerIp: String? = null,
    @Column(name = "signer_user_agent", columnDefinition = "text")
    val signerUserAgent: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @CreationTimestamp
    @Column(name = "signed_at", nullable = false, updatable = false)
    val signedAt: Instant? = null

    companion object {
        fun create(
            contractId: Long,
            userId: Long,
            partyType: PartyType,
            signatureData: String,
            pdfVersionAtSign: Int,
            signerIp: String? = null,
            signerUserAgent: String? = null,
        ): ContractSignature =
            ContractSignature(
                contractId = contractId,
                userId = userId,
                partyType = partyType,
                signatureData = signatureData,
                pdfVersionAtSign = pdfVersionAtSign,
                signerIp = signerIp,
                signerUserAgent = signerUserAgent,
            )
    }
}
