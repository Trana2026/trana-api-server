package com.trana.identity.adapter.pass

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.Base64

/**
 * mok_keyInfo.dat (base64 환경변수) 1회 복호화 + PassKeyMaterial 메모리 캐시.
 *
 * 흐름 (lazy, 첫 호출 시점):
 * 1. properties.keyInfoBase64 → Base64 decode → 암호화된 키 파일 bytes
 * 2. PassCryptoUtil.deriveKeyInfoAesKey(password) → AES key + IV
 * 3. PassCryptoUtil.aesCbcDecrypt → UTF-8 JSON
 * 4. JSON 파싱 { ServiceId, ClientPrivateKey, ServerPublicKey } (PascalCase)
 * 5. ClientPrivateKey (PKCS#8) / ServerPublicKey (X.509) → 키 객체
 *
 * lazy init 선택 이유:
 * - dummy 환경변수로도 ApplicationContext 부팅 OK (test/CI)
 * - 실 mok_keyInfo.dat 검증은 첫 PASS endpoint 호출 시점 (fail-fast in prod)
 * - by lazy SYNCHRONIZED — Spring singleton + 멀티 스레드 안전
 */
@Component
class PassKeyInfoLoader(
    private val properties: PassProperties,
    private val objectMapper: ObjectMapper,
) {
    private val keyMaterial: PassKeyMaterial by lazy { loadAndDecrypt() }

    fun get(): PassKeyMaterial = keyMaterial

    private fun loadAndDecrypt(): PassKeyMaterial {
        val encryptedBytes = Base64.getDecoder().decode(properties.keyInfoBase64)
        val (aesKey, iv) = PassCryptoUtil.deriveKeyInfoAesKey(properties.keyInfoPassword)
        val decrypted = PassCryptoUtil.aesCbcDecrypt(encryptedBytes, aesKey, iv)
        val json = String(decrypted, Charsets.UTF_8)
        val parsed = objectMapper.readValue(json, KeyInfoJson::class.java)
        return PassKeyMaterial(
            serviceId = parsed.serviceId,
            clientPrivateKey = PassCryptoUtil.parseRsaPrivateKey(parsed.clientPrivateKey),
            serverPublicKey = PassCryptoUtil.parseRsaPublicKey(parsed.serverPublicKey),
        )
    }

    /**
     * mok_keyInfo.dat 내부 JSON 매핑 (PascalCase 키 → camelCase 프로퍼티).
     */
    private data class KeyInfoJson(
        @JsonProperty("ServiceId") val serviceId: String,
        @JsonProperty("ClientPrivateKey") val clientPrivateKey: String,
        @JsonProperty("ServerPublicKey") val serverPublicKey: String,
    )
}
