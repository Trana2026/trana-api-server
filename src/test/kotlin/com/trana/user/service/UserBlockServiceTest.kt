package com.trana.user.service

import com.trana.audit.AuditLogger
import com.trana.user.UserException
import com.trana.user.entity.User
import com.trana.user.entity.UserBlock
import com.trana.user.repository.UserBlockRepository
import com.trana.user.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional

class UserBlockServiceTest {
    private val userBlockRepository: UserBlockRepository = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk()
    private val auditLogger: AuditLogger = mockk(relaxed = true)

    private val service = UserBlockService(userBlockRepository, userRepository, auditLogger)

    private fun blockedUser(): User =
        mockk(relaxed = true) {
            every { id } returns BLOCKED
            every { shareCode } returns "AB3K9"
        }

    @Test
    fun blockRejectsSelf() {
        Assertions.assertThrows(UserException.CannotBlockSelf::class.java) {
            service.block(BLOCKER, BLOCKER)
        }
        verify(exactly = 0) { userBlockRepository.save(any()) }
    }

    @Test
    fun blockSavesAndAuditsWhenNotYetBlocked() {
        every { userRepository.findById(BLOCKED) } returns Optional.of(blockedUser())
        every { userBlockRepository.existsByBlockerUserIdAndBlockedUserId(BLOCKER, BLOCKED) } returns false
        every { userBlockRepository.save(any()) } returns
            mockk<UserBlock>(relaxed = true) { every { createdAt } returns Instant.EPOCH }

        val response = service.block(BLOCKER, BLOCKED)

        Assertions.assertEquals("AB3K9", response.blockedShareCode)
        verify(exactly = 1) { userBlockRepository.save(any()) }
    }

    @Test
    fun blockIsIdempotentWhenAlreadyBlocked() {
        every { userRepository.findById(BLOCKED) } returns Optional.of(blockedUser())
        every { userBlockRepository.existsByBlockerUserIdAndBlockedUserId(BLOCKER, BLOCKED) } returns true
        val existing =
            mockk<UserBlock>(relaxed = true) {
                every { blockedUserId } returns BLOCKED
                every { createdAt } returns Instant.EPOCH
            }
        every { userBlockRepository.findAllByBlockerUserIdOrderByCreatedAtDesc(BLOCKER) } returns listOf(existing)

        service.block(BLOCKER, BLOCKED)

        verify(exactly = 0) { userBlockRepository.save(any()) }
    }

    @Test
    fun blockedCreatorIdsDelegatesToRepository() {
        every { userBlockRepository.findBlockedUserIds(BLOCKER) } returns listOf(BLOCKED, 300L)

        Assertions.assertEquals(setOf(BLOCKED, 300L), service.blockedCreatorIds(BLOCKER))
    }

    companion object {
        private const val BLOCKER = 100L
        private const val BLOCKED = 200L
    }
}
