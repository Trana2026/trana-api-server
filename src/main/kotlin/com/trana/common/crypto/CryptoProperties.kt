package com.trana.common.crypto

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 암호화 설정 — application.yml의 trana.crypto.* 매핑.
 *
 * - password: PBKDF2 입력 패스워드 (32바이트 이상 권장)
 * - salt: PBKDF2 입력 salt (hex 인코딩, 8바이트 이상 → 16자 hex 이상)
 */
@ConfigurationProperties(prefix = "trana.crypto")
data class CryptoProperties(
    val password: String,
    val salt: String,
)
