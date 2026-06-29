package com.trana.guardian.repository

import com.trana.guardian.entity.Guardian
import org.springframework.data.jpa.repository.JpaRepository

interface GuardianRepository : JpaRepository<Guardian, Long> {
    /** upsert 키 — 동일 identifier_hash면 같은 보호자 */
    fun findByIdentifierHash(identifierHash: String): Guardian?

    /** PASS upsert 키 — 동일 ci_hash 면 같은 보호자 (Option B 기준 partial unique) */
    fun findByCiHash(ciHash: String): Guardian?
}
