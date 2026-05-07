package com.trana.auth.jwt

import org.junit.jupiter.api.Assertions
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
        Assertions.assertNotNull(token)
        Assertions.assertTrue(token.isNotBlank())
    }

    @Test
    fun extractUserIdFromAccessTokenReturnsOriginalUserId() {
        val token = provider.createAccessToken(userId = 123L)
        Assertions.assertEquals(123L, provider.extractUserId(token))
    }

    @Test
    fun extractUserIdFromRefreshTokenReturnsOriginalUserId() {
        val token = provider.createRefreshToken(userId = 456L)
        Assertions.assertEquals(456L, provider.extractUserId(token))
    }

    @Test
    fun verifyWithDifferentSecretThrows() {
        val token = provider.createAccessToken(userId = 123L)
        val otherProvider = JwtProvider(
            properties.copy(
                secret = "different-secret-do-not-use-in-prod-32bytes-other-key-required",
            ),
        )
        Assertions.assertThrows(Exception::class.java) {
            otherProvider.verify(token)
        }
    }
}
