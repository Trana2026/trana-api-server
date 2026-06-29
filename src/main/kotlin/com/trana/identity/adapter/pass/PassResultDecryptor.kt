package com.trana.identity.adapter.pass

import com.fasterxml.jackson.annotation.JsonProperty
import com.trana.user.entity.Gender
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64

/**
 * mobileOK encryptMOKResult 복호화 + 무결성 검증.
 *
 * 흐름 (스펙 5단계):
 * 1. encryptMOKResult '|' 분리 → encryptKeyIvHashData | encryptResultData
 * 2. RSA-OAEP 복호화 (ClientPrivateKey) → "<base64(key+iv)>|<base64(SHA-256(data))>"
 * 3. base64 디코드 keyIv (48B) → AES key (32B) + IV (16B)
 * 4. AES/CBC/PKCS5Padding 복호화 → UTF-8 JSON
 * 5. SHA-256(JSON) base64 == hashData 확인 (변조 검증)
 *
 * 모든 검증 실패는 throw — 호출자 (PassReturnService) 가 401/422 매핑.
 */
@Component
class PassResultDecryptor(
    private val keyInfoLoader: PassKeyInfoLoader,
    private val objectMapper: ObjectMapper,
) {
    fun decrypt(encryptMOKResult: String): PassResultPayload {
        val (encKeyIvHash, encData) = splitEncryptMOKResult(encryptMOKResult)

        // 2. RSA 복호화 → "<base64KeyIv>|<hashData>"
        val keyIvHashStr =
            String(
                PassCryptoUtil.rsaOaepDecrypt(
                    Base64.getDecoder().decode(encKeyIvHash),
                    keyInfoLoader.get().clientPrivateKey,
                ),
                Charsets.UTF_8,
            )
        val (base64KeyIv, hashData) = splitKeyIvHash(keyIvHashStr)

        // 3. keyIv 분리
        val keyIv = Base64.getDecoder().decode(base64KeyIv)
        check(keyIv.size == AES_KEY_IV_BYTES) {
            "keyIv length invalid (expected $AES_KEY_IV_BYTES, got ${keyIv.size})"
        }
        val aesKey = keyIv.copyOfRange(0, AES_KEY_BYTES)
        val iv = keyIv.copyOfRange(AES_KEY_BYTES, AES_KEY_IV_BYTES)

        // 4. AES 복호화 → JSON
        val plain = PassCryptoUtil.aesCbcDecrypt(Base64.getDecoder().decode(encData), aesKey, iv)

        // 5. 무결성 검증
        val computedHash = Base64.getEncoder().encodeToString(PassCryptoUtil.sha256(plain))
        check(computedHash == hashData) {
            "encryptMOKResult 무결성 검증 실패 (hash mismatch)"
        }

        return objectMapper.readValue(String(plain, Charsets.UTF_8), PassResultPayload::class.java)
    }

    private fun splitEncryptMOKResult(value: String): Pair<String, String> {
        val parts = value.split("|")
        require(parts.size == 2) {
            "encryptMOKResult format invalid (expected '<keyIvHash>|<data>')"
        }
        return parts[0] to parts[1]
    }

    private fun splitKeyIvHash(value: String): Pair<String, String> {
        val parts = value.split("|")
        require(parts.size == 2) {
            "keyIvHashData format invalid (expected '<base64KeyIv>|<base64Hash>')"
        }
        return parts[0] to parts[1]
    }

    companion object {
        private const val AES_KEY_BYTES = 32
        private const val AES_KEY_IV_BYTES = 48
    }
}

/**
 * mobileOK 표준창 본인확인 결과 (encryptMOKResult 복호화 후 JSON).
 *
 * Spec 필드:
 * - clientTxId: 우리 측 요청 거래 ID (req-client-info 발급 시) — PENDING verification 매핑 키
 * - ci/di: 한국 본인확인 표준 식별자 (CI = 다 매핑, DI = 사이트별)
 * - userBirthday: yyyyMMdd
 * - userGender: "1" 남자 / "2" 여자
 * - userNation: "0" 내국인 / "1" 외국인
 */
data class PassResultPayload(
    @JsonProperty("siteId") val siteId: String? = null,
    @JsonProperty("clientTxId") val clientTxId: String,
    @JsonProperty("txId") val txId: String? = null,
    @JsonProperty("providerId") val providerId: String? = null,
    @JsonProperty("serviceType") val serviceType: String? = null,
    @JsonProperty("ci") val ci: String,
    @JsonProperty("di") val di: String? = null,
    @JsonProperty("userName") val userName: String,
    @JsonProperty("userPhone") val userPhone: String,
    @JsonProperty("userBirthday") val userBirthday: String,
    @JsonProperty("userGender") val userGender: String,
    @JsonProperty("userNation") val userNation: String? = null,
    @JsonProperty("reqAuthType") val reqAuthType: String? = null,
    @JsonProperty("reqDate") val reqDate: String? = null,
    @JsonProperty("issuer") val issuer: String? = null,
    @JsonProperty("issueDate") val issueDate: String? = null,
)

private val PASS_BIRTHDAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

/** PASS userBirthday "yyyyMMdd" → LocalDate. */
fun PassResultPayload.toBirthDate(): LocalDate = LocalDate.parse(userBirthday, PASS_BIRTHDAY_FORMATTER)

/** PASS userGender "1"/"2" → Gender enum. */
fun PassResultPayload.toGender(): Gender =
    when (userGender) {
        "1" -> Gender.MALE
        "2" -> Gender.FEMALE
        else -> error("Unknown PASS userGender: $userGender")
    }
