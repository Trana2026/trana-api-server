package com.trana.common.crypto

import java.security.MessageDigest
import java.util.HexFormat

/**
 * SHA-256 해시 유틸 — 변조 감지 / 무결성 검증 (약관 본문 hash 등).
 *
 * - hash: ByteArray → ByteArray (raw)
 * - hashHex: String (UTF-8) → 64자 hex 문자열
 */
object Sha256Hasher {
    fun hash(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)

    fun hashHex(input: String): String = HexFormat.of().formatHex(hash(input.toByteArray(Charsets.UTF_8)))
}
