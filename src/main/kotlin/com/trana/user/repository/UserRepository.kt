package com.trana.user.repository

import com.trana.user.entity.User
import com.trana.user.entity.UserStatus
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByPublicCode(publicCode: String): User?

    /**
     * 신뢰 점수 범위 내 활성 user 일괄 조회.
     * 면제 티켓 매월 발급 batch — 신뢰 등급 (55~74) / 우수 등급 (75~89) 별 호출.
     */
    fun findAllByStatusAndTrustScoreBetween(
        status: UserStatus,
        min: Int,
        max: Int,
    ): List<User>
}
