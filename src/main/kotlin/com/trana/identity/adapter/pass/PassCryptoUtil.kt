package com.trana.identity.adapter.pass

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/**
 * mobileOK 표준창 (PASS) 암복호화 유틸. Spring 의존성 없는 순수 함수 모음.
 *
 * 책임:
 * - mok_keyInfo.dat 복호화용 키 파생 (SHA-256 두번 → AES key + IV)
 * - AES/CBC/PKCS5Padding 복호화
 * - RSA/ECB/OAEPWithSHA-256AndMGF1Padding 암/복호화
 * - SHA-256 hash (encryptMOKResult 무결성 검증)
 * - Base64(PKCS#8/X.509) → 키 객체 파싱
 */
object PassCryptoUtil {
    private const val AES_KEY_BYTES = 32
    private const val AES_HALF_BYTES = 16
    private const val IV_BYTES = 16
    private const val SHA256 = "SHA-256"
    private const val RSA_OAEP_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    private const val AES_CBC_TRANSFORMATION = "AES/CBC/PKCS5Padding"

    /**
     * mok_keyInfo.dat 복호화용 AES 키 + IV 파생.
     * - Hash1 = SHA-256(password)
     * - Hash2 = SHA-256(Hash1)
     * - AES key = Hash1[0..15] ++ Hash2[16..31] (32B)
     * - AES IV  = Hash2[0..15] (16B)
     */
    fun deriveKeyInfoAesKey(password: String): Pair<ByteArray, ByteArray> {
        val digest = MessageDigest.getInstance(SHA256)
        val hash1 = digest.digest(password.toByteArray(Charsets.UTF_8))
        digest.reset()
        val hash2 = digest.digest(hash1)
        val key =
            ByteArray(AES_KEY_BYTES).apply {
                System.arraycopy(hash1, 0, this, 0, AES_HALF_BYTES)
                System.arraycopy(hash2, AES_HALF_BYTES, this, AES_HALF_BYTES, AES_HALF_BYTES)
            }
        val iv = hash2.copyOfRange(0, IV_BYTES)
        return key to iv
    }

    /**
     * AES/CBC/PKCS5Padding 복호화.
     */
    fun aesCbcDecrypt(
        encrypted: ByteArray,
        key: ByteArray,
        iv: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance(AES_CBC_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(encrypted)
    }

    /**
     * RSA-OAEP(SHA-256, MGF1-SHA-256) 암호화 — encryptReqClientInfo 생성 시 서버 공개키 사용.
     */
    fun rsaOaepEncrypt(
        plain: ByteArray,
        publicKey: PublicKey,
    ): ByteArray {
        val cipher = Cipher.getInstance(RSA_OAEP_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepParams())
        return cipher.doFinal(plain)
    }

    /**
     * RSA-OAEP(SHA-256, MGF1-SHA-256) 복호화 — encryptMOKResult 의 keyIvHash 복호화 시 클라이언트 개인키 사용.
     */
    fun rsaOaepDecrypt(
        encrypted: ByteArray,
        privateKey: PrivateKey,
    ): ByteArray {
        val cipher = Cipher.getInstance(RSA_OAEP_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepParams())
        return cipher.doFinal(encrypted)
    }

    /**
     * SHA-256 hash — encryptMOKResult 의 hashData 비교 (데이터 변조 검증).
     */
    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance(SHA256).digest(data)

    /**
     * Base64(PKCS#8) → RSA 개인키. mok_keyInfo.dat 의 ClientPrivateKey 파싱.
     */
    fun parseRsaPrivateKey(base64Pkcs8: String): PrivateKey {
        val bytes = Base64.getDecoder().decode(base64Pkcs8)
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(bytes))
    }

    /**
     * Base64(X.509) → RSA 공개키. mok_keyInfo.dat 의 ServerPublicKey 파싱.
     */
    fun parseRsaPublicKey(base64X509: String): PublicKey {
        val bytes = Base64.getDecoder().decode(base64X509)
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes))
    }

    private fun oaepParams(): OAEPParameterSpec =
        OAEPParameterSpec(SHA256, "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)
}
