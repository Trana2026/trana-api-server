package com.trana.common.crypto

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class EncryptorTest {
    private val properties =
        CryptoProperties(
            password = "test-password-do-not-use-in-prod",
            salt = "deadbeefcafebabe1234567890abcdef",
        )
    private val config = CryptoConfig()
    private val textEncryptor = config.textEncryptor(properties)
    private val bytesEncryptor = config.bytesEncryptor(properties)

    @Test
    fun textEncryptorRoundTripReturnsOriginal() {
        val plain = "홍길동"
        val cipher = textEncryptor.encrypt(plain)
        Assertions.assertNotEquals(plain, cipher)
        Assertions.assertEquals(plain, textEncryptor.decrypt(cipher))
    }

    @Test
    fun bytesEncryptorRoundTripReturnsOriginal() {
        val plain = "010-1234-5678".toByteArray()
        val cipher = bytesEncryptor.encrypt(plain)
        Assertions.assertArrayEquals(plain, bytesEncryptor.decrypt(cipher))
    }

    @Test
    fun encryptingSamePlaintextTwiceYieldsDifferentCiphertexts() {
        val plain = "홍길동"
        val a = textEncryptor.encrypt(plain)
        val b = textEncryptor.encrypt(plain)
        Assertions.assertNotEquals(a, b)
        Assertions.assertEquals(plain, textEncryptor.decrypt(a))
        Assertions.assertEquals(plain, textEncryptor.decrypt(b))
    }

    @Test
    fun tamperedCiphertextThrows() {
        val cipher = textEncryptor.encrypt("홍길동")
        val tampered = cipher.dropLast(1) + if (cipher.last() == '0') '1' else '0'
        Assertions.assertThrows(Exception::class.java) {
            textEncryptor.decrypt(tampered)
        }
    }

    @Test
    fun decryptWithDifferentSaltThrows() {
        val cipher = textEncryptor.encrypt("홍길동")
        val otherEncryptor =
            config.textEncryptor(
                properties.copy(salt = "ffffffffffffffffffffffffffffffff"),
            )
        Assertions.assertThrows(Exception::class.java) {
            otherEncryptor.decrypt(cipher)
        }
    }
}
