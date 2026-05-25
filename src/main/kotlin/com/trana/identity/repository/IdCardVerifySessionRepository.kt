package com.trana.identity.repository

import com.trana.identity.entity.IdCardVerifySession
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface IdCardVerifySessionRepository : JpaRepository<IdCardVerifySession, String> {
    /**
     * Cleanup task용 — 만료된 세션 목록 조회.
     * 각 row마다 S3 객체 + identity_verifications PENDING row 정리 후 deleteById 호출.
     */
    fun findAllByExpiresAtBefore(now: Instant): List<IdCardVerifySession>
}
