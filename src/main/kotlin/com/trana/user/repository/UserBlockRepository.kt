package com.trana.user.repository

import com.trana.user.entity.UserBlock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserBlockRepository : JpaRepository<UserBlock, Long> {
    fun existsByBlockerUserIdAndBlockedUserId(
        blockerUserId: Long,
        blockedUserId: Long,
    ): Boolean

    fun deleteByBlockerUserIdAndBlockedUserId(
        blockerUserId: Long,
        blockedUserId: Long,
    ): Long

    fun findAllByBlockerUserIdOrderByCreatedAtDesc(blockerUserId: Long): List<UserBlock>

    /** 계약 가시성 필터용 — blocker 가 차단한 사용자 id 집합. */
    @Query("SELECT b.blockedUserId FROM UserBlock b WHERE b.blockerUserId = :blockerUserId")
    fun findBlockedUserIds(
        @Param("blockerUserId") blockerUserId: Long,
    ): List<Long>
}
