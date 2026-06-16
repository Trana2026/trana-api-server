package com.trana.notification.service

import com.trana.common.crypto.Sha256Hasher
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
) {
    /**
     * 디바이스 토큰 등록 (멱등).
     *
     * - 같은 token_hash 기존 row + 같은 user → no-op
     * - 같은 token_hash 기존 row + 다른 user → reassignTo (단말 재로그인)
     * - 없으면 새 INSERT
     *
     * dirty checking 으로 reassignTo 후 자동 UPDATE — 명시 save 불필요.
     */
    fun register(
        userId: Long,
        token: String,
        platform: DevicePlatform,
    ): DeviceToken {
        val tokenHash = Sha256Hasher.hashHex(token)
        val existing = repository.findByTokenHash(tokenHash)
        if (existing != null) {
            if (existing.userId != userId) {
                existing.reassignTo(userId)
            }
            return existing
        }
        return repository.save(
            DeviceToken(
                userId = userId,
                tokenEncrypted = textEncryptor.encrypt(token),
                tokenHash = tokenHash,
                platform = platform,
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
}
