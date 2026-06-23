package com.trana.user.repository

import com.trana.user.entity.User
import com.trana.user.entity.UserStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

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

    /**
     * 점수 이력 cleanup 대상 — 탈퇴 1년 경과 + 사기 확인 이력 0건.
     * 사기 확인 이력 있는 user 는 영구 보존 (명세 부록).
     */
    fun findAllByStatusAndWithdrawnAtBeforeAndFraudReportReceivedCount(
        status: UserStatus,
        withdrawnAtThreshold: Instant,
        fraudReportReceivedCount: Int,
    ): List<User>
}
