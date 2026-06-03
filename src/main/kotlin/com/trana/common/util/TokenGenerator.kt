package com.trana.common.util

import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import org.springframework.stereotype.Component
import java.security.SecureRandom

/**
 * 외부 노출 토큰 통합 생성기 (refactor pp).
 *
 * - base62 (영문 대소문자 + 숫자), URL-safe, SecureRandom 예측 불가
 * - 도메인별 메서드로 의미 명시:
 *   - PublicCode (12자): user / contract 외부 식별자 — 충돌 확률 ≈ 1/3.2 × 10^21
 *   - GuardianLink (21자): 보호자 KYC 링크 — 충돌 확률 ≈ 1/4.5 × 10^37
 *   - ContractInvitation (21자): 수신자 초대 링크 — 본인 인증 전 통과 가능 → 추측 공격 차단
 * - 통합 전: PublicCodeGenerator / GuardianLinkTokenGenerator / ContractInvitationTokenGenerator 3개 동일 알고리즘 중복
 */
@Component
class TokenGenerator {
    fun generatePublicCode(): String = generate(PUBLIC_CODE_LENGTH)

    fun generateGuardianLink(): String = generate(GUARDIAN_LINK_LENGTH)

    fun generateContractInvitation(): String = generate(CONTRACT_INVITATION_LENGTH)

    private fun generate(length: Int): String = NanoIdUtils.randomNanoId(RANDOM, ALPHABET, length)

    companion object {
        private val RANDOM = SecureRandom()
        private val ALPHABET: CharArray =
            (
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                    "abcdefghijklmnopqrstuvwxyz" +
                    "0123456789"
            ).toCharArray()
        private const val PUBLIC_CODE_LENGTH = 12
        private const val GUARDIAN_LINK_LENGTH = 21
        private const val CONTRACT_INVITATION_LENGTH = 21
    }
}
