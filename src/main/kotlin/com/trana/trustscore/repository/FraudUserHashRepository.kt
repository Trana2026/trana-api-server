package com.trana.trustscore.repository

import com.trana.trustscore.entity.FraudUserHash
import org.springframework.data.jpa.repository.JpaRepository

interface FraudUserHashRepository : JpaRepository<FraudUserHash, Long> {
    /** 탈퇴 처리 시점에 기존 row 조회 — 없으면 신규 INSERT, 있으면 markWithdrawn(). */
    fun findByUserIdHash(userIdHash: String): FraudUserHash?

    /** 재가입 차단 검사 등에서 사용 (W10+ B2B API). */
    fun existsByUserIdHash(userIdHash: String): Boolean

    /** PASS 재가입 차단 — 같은 ci 의 사기 확인 user 가 있으면 신규 PASS 가입 차단 */
    fun existsByCiHash(ciHash: String): Boolean
}
