package com.trana.terms.entity

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

@Entity
@Table(name = "terms_versions")
class TermsVersion(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: TermsType,
    @Column(nullable = false, length = 20)
    val version: String,
    @Column(nullable = false, length = 200)
    val title: String,
    @Column(name = "content_url", nullable = false, columnDefinition = "TEXT")
    val contentUrl: String,
    @Column(name = "content_hash", nullable = false, length = 64)
    val contentHash: String,
    @Column(name = "effective_at", nullable = false)
    val effectiveAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null
}

enum class TermsType {
    SERVICE,
    PRIVACY,
    THIRD_PARTY,
    MARKETING,
    LOCATION,
    CONTRACT_AGREEMENT,
    ELECTRONIC_SIGNATURE,
}
