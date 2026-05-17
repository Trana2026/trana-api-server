package com.trana.identity

import org.springframework.data.jpa.repository.JpaRepository
import java.time.OffsetDateTime

interface IdCardVerifySessionRepository : JpaRepository<IdCardVerifySession, String> {
    /**
     * 만료된 세션 일괄 삭제 (cleanup task에서 호출).
     * 호출자는 @Transactional 필요 (Spring Data derived delete는 트랜잭션 안에서 동작).
     */
    fun deleteByExpiresAtBefore(threshold: OffsetDateTime): Long
}
