package com.trana.common.util

import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import org.springframework.stereotype.Component
import java.security.SecureRandom

/**
 * 계약 수신자 초대 토큰 생성기.
 *
 * - 21자 base62 (충돌 확률: 약 1/(62^21) ≈ 1/4.5 × 10^37)
 * - URL-safe
 * - SecureRandom — 추측 공격 차단 (수신자 초대 링크가 외부 노출 + 본인 인증 전 통과 가능)
 * - 알고리즘은 [GuardianLinkTokenGenerator] 와 동일. 도메인 분리 위해 별도 컴포넌트
 *   (추후 공통 LinkTokenGenerator 로 통합 가능 — 보류 항목 후보)
 */
@Component
class ContractInvitationTokenGenerator {
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
