package com.trana.guardian

import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import org.springframework.stereotype.Component
import java.security.SecureRandom

/**
 * 보호자 매칭 토큰 생성기.
 *
 * - 21자 base62 (URL-safe, SecureRandom)
 * - 충돌 확률 ≈ 1/(62^21) ≈ 1/4.0 × 10^37 (사실상 0)
 * - 추측 불가 (boot-force 무의미 + 3일 TTL + 1회 사용)
 * - PublicCodeGenerator(12자)와 길이로 구분 → URL/로그에서 즉시 식별 가능
 */
@Component
class GuardianTokenGenerator {
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
