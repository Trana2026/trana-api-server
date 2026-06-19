package com.trana.user.repository

import com.trana.user.entity.UserInquiry
import org.springframework.data.jpa.repository.JpaRepository

interface UserInquiryRepository : JpaRepository<UserInquiry, Long> {
    /** 마이페이지 목록 — 본인 row 최신순. */
    fun findAllByUserIdOrderByCreatedAtDesc(userId: Long): List<UserInquiry>

    /** 마이페이지 상세 — 본인 row 만 (다른 user 의 publicCode 추측 차단 → null 시 404). */
    fun findByPublicCodeAndUserId(
        publicCode: String,
        userId: Long,
    ): UserInquiry?
}
