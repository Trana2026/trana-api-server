package com.trana.identity.adapter.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PassCryptoUtilTest {
    @Test
    fun derivedKeyHasExpectedLengths() {
        val (key, iv) = PassCryptoUtil.deriveKeyInfoAesKey("password123")
        assertEquals(AES_KEY_BYTES, key.size)
        assertEquals(IV_BYTES, iv.size)
    }

    @Test
    fun derivedKeyIsDeterministic() {
        val (key1, iv1) = PassCryptoUtil.deriveKeyInfoAesKey("password123")
        val (key2, iv2) = PassCryptoUtil.deriveKeyInfoAesKey("password123")
        assertContentEquals(key1, key2)
        assertContentEquals(iv1, iv2)
    }

    @Test
    fun derivedKeyFollowsHash1Hash2Composition() {
        val password = "myPassword!@#"
        val sha = MessageDigest.getInstance("SHA-256")
        val hash1 = sha.digest(password.toByteArray(Charsets.UTF_8))
        sha.reset()
        val hash2 = sha.digest(hash1)
        val expectedKey = hash1.copyOfRange(0, AES_HALF_BYTES) + hash2.copyOfRange(AES_HALF_BYTES, AES_KEY_BYTES)
        val expectedIv = hash2.copyOfRange(0, IV_BYTES)

        val (actualKey, actualIv) = PassCryptoUtil.deriveKeyInfoAesKey(password)

        assertContentEquals(expectedKey, actualKey)
        assertContentEquals(expectedIv, actualIv)
    }

    @Test
    fun aesCbcRoundTripRecoversPlaintext() {
        val (key, iv) = PassCryptoUtil.deriveKeyInfoAesKey("password123")
        val plain = "Hello, mobileOK 표준창!".toByteArray(Charsets.UTF_8)
        val encrypted = encryptAesCbc(plain, key, iv)

        val decrypted = PassCryptoUtil.aesCbcDecrypt(encrypted, key, iv)

        assertContentEquals(plain, decrypted)
    }

    @Test
    fun aesCbcDecryptWithWrongKeyThrows() {
        val (rightKey, iv) = PassCryptoUtil.deriveKeyInfoAesKey("right-password")
        val (wrongKey, _) = PassCryptoUtil.deriveKeyInfoAesKey("wrong-password")
        val encrypted = encryptAesCbc("data".toByteArray(), rightKey, iv)

        assertThrows<BadPaddingException> {
            PassCryptoUtil.aesCbcDecrypt(encrypted, wrongKey, iv)
        }
    }

    @Test
    fun rsaOaepRoundTripRecoversPlaintext() {
        val keyPair = generateRsaKeyPair()
        val plain = "ORG001-550e8400-e29b-41d4".toByteArray(Charsets.UTF_8)

        val encrypted = PassCryptoUtil.rsaOaepEncrypt(plain, keyPair.public)
        val decrypted = PassCryptoUtil.rsaOaepDecrypt(encrypted, keyPair.private)

        assertContentEquals(plain, decrypted)
    }

    @Test
    fun rsaOaepEncryptProducesDifferentCipherEachCall() {
        // OAEP padding randomizes — 같은 plain + 같은 key 라도 매번 다른 cipher
        val keyPair = generateRsaKeyPair()
        val plain = "deterministic".toByteArray()

        val c1 = PassCryptoUtil.rsaOaepEncrypt(plain, keyPair.public)
        val c2 = PassCryptoUtil.rsaOaepEncrypt(plain, keyPair.public)

        assertFalse(c1.contentEquals(c2))
        assertContentEquals(plain, PassCryptoUtil.rsaOaepDecrypt(c1, keyPair.private))
        assertContentEquals(plain, PassCryptoUtil.rsaOaepDecrypt(c2, keyPair.private))
    }

    @Test
    fun sha256MatchesKnownEmptyVector() {
        // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        val hex = PassCryptoUtil.sha256(ByteArray(0)).toHex()
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hex)
    }

    @Test
    fun sha256MatchesKnownAbcVector() {
        // SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        val hex = PassCryptoUtil.sha256("abc".toByteArray(Charsets.UTF_8)).toHex()
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex)
    }

    @Test
    fun parseRsaPrivateAndPublicKeysRecoverFromBase64Encoding() {
        val keyPair = generateRsaKeyPair()
        val privBase64 = Base64.getEncoder().encodeToString(keyPair.private.encoded)
        val pubBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)

        val parsedPriv = PassCryptoUtil.parseRsaPrivateKey(privBase64)
        val parsedPub = PassCryptoUtil.parseRsaPublicKey(pubBase64)

        assertContentEquals(keyPair.private.encoded, parsedPriv.encoded)
        assertContentEquals(keyPair.public.encoded, parsedPub.encoded)
    }

    @Test
    fun parsedKeysComposeForRoundTripEncryption() {
        // KeyInfoLoader 시나리오 — base64 PKCS#8/X.509 로 받아서 parse + encrypt/decrypt
        val keyPair = generateRsaKeyPair()
        val privBase64 = Base64.getEncoder().encodeToString(keyPair.private.encoded)
        val pubBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val parsedPriv = PassCryptoUtil.parseRsaPrivateKey(privBase64)
        val parsedPub = PassCryptoUtil.parseRsaPublicKey(pubBase64)
        val plain = "round-trip via parsed keys".toByteArray(Charsets.UTF_8)

        val encrypted = PassCryptoUtil.rsaOaepEncrypt(plain, parsedPub)
        val decrypted = PassCryptoUtil.rsaOaepDecrypt(encrypted, parsedPriv)

        assertContentEquals(plain, decrypted)
    }

    private fun encryptAesCbc(
        plain: ByteArray,
        key: ByteArray,
        iv: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(plain)
    }

    private fun generateRsaKeyPair() =
        KeyPairGenerator.getInstance("RSA").apply { initialize(RSA_KEY_BITS) }.generateKeyPair()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val AES_KEY_BYTES = 32
        private const val AES_HALF_BYTES = 16
        private const val IV_BYTES = 16
        private const val RSA_KEY_BITS = 2048
    }
}
