package com.trana.guardian

import org.springframework.data.jpa.repository.JpaRepository

interface GuardianLinkRepository : JpaRepository<GuardianLink, Long> {
    /** 보호자가 링크 클릭 → 토큰 검증 시점 (GET /v1/guardian/links/{token}). */
    fun findByToken(token: String): GuardianLink?

    /** 재발급 시 기존 활성 토큰 만료 처리용 (한 미성년자당 보통 1~2개). */
    fun findAllByMinorUserIdAndUsedAtIsNullAndRevokedAtIsNull(minorUserId: Long): List<GuardianLink>
}
