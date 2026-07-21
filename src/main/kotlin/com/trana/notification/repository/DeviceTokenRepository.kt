package com.trana.notification.repository

import com.trana.notification.entity.DevicePlatform
import com.trana.notification.entity.DeviceToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DeviceTokenRepository : JpaRepository<DeviceToken, Long> {
    /** 등록 시 중복 체크 + invalid 응답 매칭. */
    fun findByTokenHash(tokenHash: String): DeviceToken?

    /** 한 user 의 모든 활성 토큰 — 멀티캐스트 발송 시점에 일괄 조회. */
    fun findAllByUserId(userId: Long): List<DeviceToken>

    /** 사용자 본인 해제 — DELETE /v1/users/me/device-tokens/{token} (C-4). */
    fun deleteByUserIdAndTokenHash(
        userId: Long,
        tokenHash: String,
    ): Long

    /**
     * C-5 invalid token 일괄 정리.
     *
     * LiveFcmClient.send 응답의 invalidTokens 리스트를 hash 로 변환 후 일괄 삭제.
     * 빈 collection 호출은 0 반환 (정상).
     */
    @Modifying
    @Query("DELETE FROM DeviceToken d WHERE d.tokenHash IN :hashes")
    fun deleteAllByTokenHashIn(
        @Param("hashes") hashes: Collection<String>,
    ): Int

    /** 마이페이지 기기 목록 — 본인 단말, 최신 등록 순. */
    fun findAllByUserIdOrderByCreatedAtDesc(userId: Long): List<DeviceToken>

    /**
     * 같은 물리 기기 판별용 — (user, platform, deviceModel) 매칭.
     * 재설치 시 FCM 토큰이 바뀌어 새 row 가 생기는 중복 제거에 사용 (plan 3-2).
     */
    fun findAllByUserIdAndPlatformAndDeviceModel(
        userId: Long,
        platform: DevicePlatform,
        deviceModel: String,
    ): List<DeviceToken>

    /** 마이페이지 강제 해제 — id + userId 매칭 (다른 user 의 id 추측 시 null → 404). */
    fun findByIdAndUserId(
        id: Long,
        userId: Long,
    ): DeviceToken?

    /** ping endpoint 권한 검증 — 본인 token 만 갱신 (다른 user 의 token_hash 추측 차단). */
    fun findByUserIdAndTokenHash(
        userId: Long,
        tokenHash: String,
    ): DeviceToken?
}
