package com.trana.common.util

import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import org.springframework.stereotype.Component
import java.security.SecureRandom

/**
 * 보호자 링크 토큰 생성기.
 *
 * - 21자 base62 (충돌 확률: 약 1/(62^21) ≈ 1/4.5 × 10^37)
 * - URL-safe
 * - SecureRandom (예측 불가) — 외부 노출 토큰이라 추측 공격 차단
 * - publicCode(12자)보다 길게 → 더 강한 보안성
 */
@Component
class GuardianLinkTokenGenerator {
    fun generate(): String = NanoIdUtils.randomNanoId(RANDOM, ALPHABET, LENGTH)

    companion object {
        private val RANDOM = SecureRandom()
        private val ALPHABET: CharArray =
            (
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                    "abcdefghijklmnopqrstuvwxyz" +
                    "0123456789"
            ).toCharArray()
        private const val LENGTH = 21
    }
}
