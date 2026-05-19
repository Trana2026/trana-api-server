package com.trana.identity.repository

import com.trana.identity.entity.IdCardVerifySession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface IdCardVerifySessionRepository : JpaRepository<IdCardVerifySession, String> {
    /**
     * Cleanup task용 — 만료된 세션 일괄 삭제.
     * @return 삭제된 row 수
     */
    @Modifying
    @Query("DELETE FROM IdCardVerifySession s WHERE s.expiresAt < :now")
    fun deleteExpired(
        @Param("now") now: Instant,
    ): Int
}
