package com.trana.trustscore.service

import com.trana.trustscore.entity.FraudUserHash
import com.trana.trustscore.repository.FraudUserHashRepository
import com.trana.user.entity.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant

/**
 * 사기 확인 사용자 hash 관리 서비스.
 *
 * - handleWithdrawal : 탈퇴 시 fraud_user_hashes INSERT (없으면) 또는 markWithdrawn (있으면)
 * - SHA-256 hash source = users.public_code (외부 노출 식별자, 이미 사용 중 + 순차 X)
 *
 * 명세 부록:
 * - 원본 PII 즉시 삭제 (UserService 가 user.maskFraudPii 호출) + hash + 메타데이터만 영구
 * - B2B API 사기 조회 서비스 (W10+) 의 source
 */
@Service
@Transactional
class FraudUserHashService(
    private val repository: FraudUserHashRepository,
) {
    /**
     * 탈퇴 시점에 호출 — 사기 확인 이력 있는 user 의 hash 영구 보존.
     *
     * @param user 탈퇴 처리 중인 User (fraudReportReceivedCount > 0 검증은 호출자 책임)
     */
    fun handleWithdrawal(user: User) {
        val userIdHash = sha256Hex(user.publicCode)
        val existing = repository.findByUserIdHash(userIdHash)
        if (existing != null) {
            existing.markWithdrawn()
            return
        }
        val hash =
            FraudUserHash(
                userIdHash = userIdHash,
                fraudConfirmedAt = Instant.now(),
                reason = DEFAULT_FRAUD_REASON,
                reporterIdHashes = null,
                relatedContractPublicCodes = null,
            )
        hash.markWithdrawn()
        repository.save(hash)
    }

    private fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val DEFAULT_FRAUD_REASON =
            "탈퇴 시점 사기 확인 이력 있음 (자세 사유는 dispute_records.resolution_reason 참조)"
    }
}
