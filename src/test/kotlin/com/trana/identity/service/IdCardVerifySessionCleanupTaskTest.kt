package com.trana.identity.service

import com.trana.identity.entity.IdCardVerifySession
import com.trana.identity.repository.IdCardVerifySessionRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant

class IdCardVerifySessionCleanupTaskTest {
    private val sessionRepository: IdCardVerifySessionRepository = mockk()
    private val purger: IdentitySessionPurger = mockk()

    private val task =
        IdCardVerifySessionCleanupTask(
            sessionRepository = sessionRepository,
            purger = purger,
        )

    @Test
    fun cleanupExpiredDoesNothingWhenNoExpiredSessions() {
        every { sessionRepository.findAllByExpiresAtBefore(any()) } returns emptyList()

        task.cleanupExpired()

        verify(exactly = 0) { purger.purgeByRequestId(any()) }
    }

    @Test
    fun cleanupExpiredDelegatesEachExpiredSessionToPurger() {
        val s1 = expiredSession("req-1")
        val s2 = expiredSession("req-2")
        every { sessionRepository.findAllByExpiresAtBefore(any()) } returns listOf(s1, s2)
        every { purger.purgeByRequestId("req-1") } just Runs
        every { purger.purgeByRequestId("req-2") } just Runs

        task.cleanupExpired()

        verify(exactly = 1) { purger.purgeByRequestId("req-1") }
        verify(exactly = 1) { purger.purgeByRequestId("req-2") }
    }

    private fun expiredSession(requestId: String): IdCardVerifySession =
        IdCardVerifySession(
            requestId = requestId,
            idType = "RESIDENT",
            name = "테스트",
            idCardS3Key = null,
            idCardMime = null,
            expiresAt = Instant.now().minusSeconds(60),
        )
}
