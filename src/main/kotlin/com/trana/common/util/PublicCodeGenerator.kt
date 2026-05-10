package com.trana.common.util

import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import org.springframework.stereotype.Component
import java.security.SecureRandom

/**
 * 외부 노출용 식별자 생성기.
 *
 * - 12자 base62 (충돌 확률: 약 1/(62^12) ≈ 1/3.2 × 10^21)
 * - URL-safe (영문 대소문자 + 숫자, 특수문자 없음)
 * - SecureRandom 사용 (예측 불가)
 */
@Component
class PublicCodeGenerator {
    fun generate(): String = NanoIdUtils.randomNanoId(RANDOM, ALPHABET, LENGTH)

    companion object {
        private val RANDOM = SecureRandom()
        private val ALPHABET: CharArray =
            (
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                    "abcdefghijklmnopqrstuvwxyz" +
                    "0123456789"
            ).toCharArray()
        private const val LENGTH = 12
    }
}
