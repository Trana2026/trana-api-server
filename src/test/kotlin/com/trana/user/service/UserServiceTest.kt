package com.trana.user.service

import com.trana.audit.AuditLogger
import com.trana.common.util.TokenGenerator
import com.trana.trustscore.service.FraudUserHashService
import com.trana.user.UserException
import com.trana.user.entity.User
import com.trana.user.entity.UserStatus
import com.trana.user.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.Optional

class UserServiceTest {
    private val userRepository: UserRepository = mockk()
    private val tokenGenerator: TokenGenerator = mockk()
    private val auditLogger: AuditLogger = mockk(relaxed = true)
    private val fraudUserHashService: FraudUserHashService = mockk(relaxed = true)

    private val service =
        UserService(
            userRepository = userRepository,
            tokenGenerator = tokenGenerator,
            auditLogger = auditLogger,
            fraudUserHashService = fraudUserHashService,
        )

    // ───── withdraw ─────

    @Test
    fun withdrawInvokesUserWithdrawWhenActive() {
        val user =
            mockk<User>(relaxed = true) {
                every { status } returns UserStatus.ACTIVE
                every { id } returns 1L
            }
        every { userRepository.findById(1L) } returns Optional.of(user)

        service.withdraw(1L)

        verify { user.withdraw() }
    }

    @Test
    fun withdrawThrowsWhenAlreadyWithdrawn() {
        val user =
            mockk<User>(relaxed = true) {
                every { status } returns UserStatus.WITHDRAWN
            }
        every { userRepository.findById(1L) } returns Optional.of(user)

        Assertions.assertThrows(UserException.AlreadyWithdrawn::class.java) {
            service.withdraw(1L)
        }
        verify(exactly = 0) { user.withdraw() }
    }
}
