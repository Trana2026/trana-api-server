package com.trana.notification.service

import com.trana.common.crypto.Sha256Hasher
import com.trana.notification.DeviceTokenException
import com.trana.notification.adapter.ipinfo.IpInfoClient
import com.trana.notification.entity.DevicePlatform
import com.trana.notification.entity.DeviceToken
import com.trana.notification.repository.DeviceTokenRepository
import org.springframework.security.crypto.encrypt.TextEncryptor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class DeviceTokenService(
    private val repository: DeviceTokenRepository,
    private val textEncryptor: TextEncryptor,
    private val ipInfoClient: IpInfoClient,
) {
    /**
     * 디바이스 토큰 등록 (멱등).
     *
     * - 같은 token_hash 기존 row + 같은 user → no-op (deviceModel/location 갱신 X)
     * - 같은 token_hash 기존 row + 다른 user → reassignTo (단말 재로그인, 마찬가지로 deviceModel/location 유지)
     * - 없으면 새 INSERT (deviceModel + IP → city/country 조회 결과 함께 저장)
     *
     * ip 는 nullable — IpExtractor 로 X-Forwarded-For / remoteAddr 파싱 실패 시 null 허용.
     * IpInfoClient 조회는 실패해도 null 반환 → 등록 흐름 자체는 항상 성공.
     */
    fun register(
        userId: Long,
        token: String,
        platform: DevicePlatform,
        deviceModel: String? = null,
        ip: String? = null,
    ): DeviceToken {
        val tokenHash = Sha256Hasher.hashHex(token)
        val existing = repository.findByTokenHash(tokenHash)
        if (existing != null) {
            if (existing.userId != userId) {
                existing.reassignTo(userId)
            }
            return existing
        }
        val location = ip?.let { ipInfoClient.lookup(it) }
        return repository.save(
            DeviceToken(
                userId = userId,
                tokenEncrypted = textEncryptor.encrypt(token),
                tokenHash = tokenHash,
                platform = platform,
                deviceModel = deviceModel,
                locationCity = location?.city,
                locationCountry = location?.country,
            ),
        )
    }

    /**
     * 사용자 본인 해제 (멱등).
     *
     * userId + token_hash 모두 매칭 시만 삭제 — 다른 user 의 동일 토큰은 영향 X.
     * 없어도 OK (명세서 토큰 만료 자동 정리와 동일 의미).
     */
    fun unregister(
        userId: Long,
        token: String,
    ) {
        val tokenHash = Sha256Hasher.hashHex(token)
        repository.deleteByUserIdAndTokenHash(userId, tokenHash)
    }

    /** 마이페이지 — 본인 단말 목록 (등록순 desc). */
    @Transactional(readOnly = true)
    fun listMine(userId: Long): List<DeviceToken> = repository.findAllByUserIdOrderByCreatedAtDesc(userId)

    /**
     * 마이페이지 강제 해제 — 본인 row 만 (다른 user 의 id 추측 시 404).
     *
     * FCM token 만 정리 — JWT blacklist X (stateless 정책, access 15분 자연 만료).
     * 강제 해제된 단말은 푸시 못 받음 + 15분 안에 access token 만료.
     */
    fun forceDelete(
        userId: Long,
        id: Long,
    ) {
        val token =
            repository.findByIdAndUserId(id, userId)
                ?: throw DeviceTokenException.NotFound(id)
        repository.delete(token)
    }

    /**
     * Flutter 가 앱 foreground 진입 시 호출 — 본인 token 의 lastUsedAt 갱신.
     *
     * - 본인 token 매칭 → markUsed
     * - 매칭 실패 (등록 안 된 token / 다른 user token 추측) → silent ignore (정상 200)
     * - 멱등 — 짧은 시간 내 중복 호출 OK
     */
    fun ping(
        userId: Long,
        token: String,
    ) {
        val tokenHash = Sha256Hasher.hashHex(token)
        val existing = repository.findByUserIdAndTokenHash(userId, tokenHash) ?: return
        existing.markUsed()
    }
}
