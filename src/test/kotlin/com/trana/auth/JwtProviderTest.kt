package com.trana.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class JwtProviderTest {
    private val properties = JwtProperties(
        issuer = "trana",
        audience = "trana-app",
        secret = "test-secret-do-not-use-in-prod-32bytes-minimum-length-required",
        accessTokenTtl = Duration.ofMinutes(15),
        refreshTokenTtl = Duration.ofDays(30),
    )

    private val provider = JwtProvider(properties)

    @Test
    fun createAccessTokenReturnsNonEmptyToken() {
        val token = provider.createAccessToken(userId = 123L)
        assertNotNull(token)
        assertTrue(token.isNotBlank())
    }

    @Test
    fun extractUserIdFromAccessTokenReturnsOriginalUserId() {
        val token = provider.createAccessToken(userId = 123L)
        assertEquals(123L, provider.extractUserId(token))
    }

    @Test
    fun extractUserIdFromRefreshTokenReturnsOriginalUserId() {
        val token = provider.createRefreshToken(userId = 456L)
        assertEquals(456L, provider.extractUserId(token))
    }

    @Test
    fun verifyWithDifferentSecretThrows() {
        val token = provider.createAccessToken(userId = 123L)
        val otherProvider = JwtProvider(
            properties.copy(
                secret = "different-secret-do-not-use-in-prod-32bytes-other-key-required",
            ),
        )
        assertThrows(Exception::class.java) {
            otherProvider.verify(token)
        }
    }
}
