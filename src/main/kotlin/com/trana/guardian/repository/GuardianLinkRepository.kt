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

    /**
     * 1회용 토큰 atomic 사용 처리 (refactor cc).
     *
     * - `WHERE used_at IS NULL AND expires_at > :now` 조건 만족 시 UPDATE 1 row
     * - find-then-save race 시 한쪽만 affected=1, 다른쪽 0 → 호출자가 분류
     * - 만료/이미 사용 상태도 0 으로 묶임 (호출자가 findById 로 사유 분류)
     */
    @Modifying
    @Query(
        """
      UPDATE GuardianLink l
         SET l.usedAt = :now
       WHERE l.token = :token
         AND l.usedAt IS NULL
         AND l.expiresAt > :now
      """,
    )
    fun markUsedAtomically(
        @Param("token") token: String,
        @Param("now") now: Instant,
    ): Int
}
