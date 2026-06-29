package com.trana.identity.adapter.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import java.security.KeyPairGenerator
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertSame

class PassKeyInfoLoaderTest {
    private val objectMapper: ObjectMapper = JsonMapper.builder().build()

    @Test
    fun loadsServiceIdAndKeysFromSyntheticKeyInfo() {
        val password = "test-password"
        val serviceId = "synthetic-service-id-12345"
        val clientKeyPair = generateRsaKeyPair()
        val serverKeyPair = generateRsaKeyPair()
        val base64 =
            buildSyntheticKeyInfoBase64(
                password = password,
                serviceId = serviceId,
                clientPrivateKeyBase64 = Base64.getEncoder().encodeToString(clientKeyPair.private.encoded),
                serverPublicKeyBase64 = Base64.getEncoder().encodeToString(serverKeyPair.public.encoded),
            )
        val loader = PassKeyInfoLoader(buildProperties(base64, password), objectMapper)

        val material = loader.get()

        assertEquals(serviceId, material.serviceId)
        assertContentEquals(clientKeyPair.private.encoded, material.clientPrivateKey.encoded)
        assertContentEquals(serverKeyPair.public.encoded, material.serverPublicKey.encoded)
    }

    @Test
    fun cachesKeyMaterialAcrossCalls() {
        val password = "test-password"
        val base64 =
            buildSyntheticKeyInfoBase64(
                password = password,
                serviceId = "sid",
                clientPrivateKeyBase64 = Base64.getEncoder().encodeToString(generateRsaKeyPair().private.encoded),
                serverPublicKeyBase64 = Base64.getEncoder().encodeToString(generateRsaKeyPair().public.encoded),
            )
        val loader = PassKeyInfoLoader(buildProperties(base64, password), objectMapper)

        val first = loader.get()
        val second = loader.get()

        assertSame(first, second)
    }

    @Test
    fun loadingWithWrongPasswordFails() {
        val base64 =
            buildSyntheticKeyInfoBase64(
                password = "right",
                serviceId = "sid",
                clientPrivateKeyBase64 = Base64.getEncoder().encodeToString(generateRsaKeyPair().private.encoded),
                serverPublicKeyBase64 = Base64.getEncoder().encodeToString(generateRsaKeyPair().public.encoded),
            )
        val loader = PassKeyInfoLoader(buildProperties(base64, "wrong"), objectMapper)

        assertFails { loader.get() }
    }

    @Test
    fun loadingWithInvalidBase64Throws() {
        val loader = PassKeyInfoLoader(buildProperties("!!!not-valid-base64!!!", "password"), objectMapper)

        assertThrows<IllegalArgumentException> {
            loader.get()
        }
    }

    private fun buildProperties(
        keyInfoBase64: String,
        password: String,
    ) = PassProperties(
        keyInfoBase64 = keyInfoBase64,
        keyInfoPassword = password,
        usageCode = "01005",
        serviceType = "telcoAuth",
        returnUrl = "http://localhost/test",
        mobileOkBaseUrl = "https://test.mobile-ok.example.com",
    )

    /**
     * 실 mok_keyInfo.dat 와 동일 포맷의 synthetic 키 파일 base64 생성.
     *
     * - JSON: { "ServiceId":..., "ClientPrivateKey":..., "ServerPublicKey":... }
     * - AES/CBC/PKCS5Padding 암호화 (key/IV 는 PassCryptoUtil.deriveKeyInfoAesKey 와 동일 알고리즘)
     * - Base64 인코딩
     */
    private fun buildSyntheticKeyInfoBase64(
        password: String,
        serviceId: String,
        clientPrivateKeyBase64: String,
        serverPublicKeyBase64: String,
    ): String {
        val json =
            """
            {
              "ServiceId":"$serviceId",
              "ClientPrivateKey":"$clientPrivateKeyBase64",
              "ServerPublicKey":"$serverPublicKeyBase64"
            }
            """.trimIndent()
        val (aesKey, iv) = PassCryptoUtil.deriveKeyInfoAesKey(password)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))
        val encrypted = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    private fun generateRsaKeyPair() =
        KeyPairGenerator.getInstance("RSA").apply { initialize(RSA_KEY_BITS) }.generateKeyPair()

    companion object {
        private const val RSA_KEY_BITS = 2048
    }
}
