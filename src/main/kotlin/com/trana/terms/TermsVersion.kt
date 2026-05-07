package com.trana.terms

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(
    name = "terms_versions",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["type", "version"]),
    ],
)
class TermsVersion(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: TermsType,

    @Column(nullable = false, length = 20)
    val version: String,

    @Column(nullable = false, length = 200)
    val title: String,

    @Column(name = "content_url", nullable = false)
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
    var createdAt: Instant? = null
        protected set
}

enum class TermsType {
    SERVICE,
    PRIVACY,
    MARKETING,
    LOCATION,
}
