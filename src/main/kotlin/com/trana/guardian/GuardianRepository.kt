package com.trana.guardian

import org.springframework.data.jpa.repository.JpaRepository

interface GuardianRepository : JpaRepository<Guardian, Long> {
    fun findByIdentifierHash(identifierHash: String): Guardian?

    fun findByPublicCode(publicCode: String): Guardian?
}
