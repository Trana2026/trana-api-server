package com.trana.guardian.repository

import com.trana.guardian.entity.GuardianLink
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface GuardianLinkRepository : JpaRepository<GuardianLink, String> {
    /** Cleanup task용 — 만료된 link 일괄 삭제 (사용 여부 무관, audit는 identity_verifications) */
    @Modifying
    @Query("DELETE FROM GuardianLink l WHERE l.expiresAt < :now")
    fun deleteExpired(
        @Param("now") now: Instant,
    ): Int
}
