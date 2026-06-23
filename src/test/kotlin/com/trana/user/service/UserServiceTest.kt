package com.trana.user.service

import com.trana.audit.AuditLogger
import com.trana.auth.oauth.SocialProvider
import com.trana.common.util.TokenGenerator
import com.trana.trustscore.service.FraudUserHashService
import com.trana.user.UserException
import com.trana.user.entity.AgeGroup
import com.trana.user.entity.SocialAccount
import com.trana.user.entity.User
import com.trana.user.entity.UserStatus
import com.trana.user.repository.SocialAccountRepository
import com.trana.user.repository.UserRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.Optional

class UserServiceTest {
    private val userRepository: UserRepository = mockk()
    private val socialAccountRepository: SocialAccountRepository = mockk()
    private val tokenGenerator: TokenGenerator = mockk()
    private val auditLogger: AuditLogger = mockk(relaxed = true)
    private val fraudUserHashService: FraudUserHashService = mockk(relaxed = true)

    private val service =
        UserService(
            userRepository = userRepository,
            socialAccountRepository = socialAccountRepository,
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

    // ───── findOrCreateBySocial 재가입 ─────

    @Test
    fun findOrCreateBySocialReturnsActiveExistingUser() {
        val existingSocial = SocialAccount(userId = 1L, provider = SocialProvider.KAKAO, providerUserId = "k-1")
        val existingUser = mockk<User>(relaxed = true) { every { status } returns UserStatus.ACTIVE }
        every {
            socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "k-1")
        } returns existingSocial
        every { userRepository.findById(1L) } returns Optional.of(existingUser)

        val result =
            service.findOrCreateBySocial(
                provider = SocialProvider.KAKAO,
                providerUserId = "k-1",
                ageGroup = AgeGroup.MINOR,
            )

        Assertions.assertSame(existingUser, result)
        verify(exactly = 0) { userRepository.save(any<User>()) }
        verify(exactly = 0) { socialAccountRepository.delete(any<SocialAccount>()) }
    }

    @Test
    fun findOrCreateBySocialDeletesOldAndCreatesNewWhenWithdrawn() {
        val existingSocial = SocialAccount(userId = 1L, provider = SocialProvider.KAKAO, providerUserId = "k-1")
        val withdrawnUser = mockk<User>(relaxed = true) { every { status } returns UserStatus.WITHDRAWN }
        every {
            socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "k-1")
        } returns existingSocial
        every { userRepository.findById(1L) } returns Optional.of(withdrawnUser)
        every { socialAccountRepository.delete(existingSocial) } just Runs
        every { socialAccountRepository.flush() } just Runs
        every { tokenGenerator.generatePublicCode() } returns "PUBLIC-2"
        every { userRepository.save(any<User>()) } answers { firstArg<User>().withId(2L) }
        every { socialAccountRepository.save(any<SocialAccount>()) } answers { firstArg() }

        val result =
            service.findOrCreateBySocial(
                provider = SocialProvider.KAKAO,
                providerUserId = "k-1",
                ageGroup = AgeGroup.MINOR,
            )

        verify { socialAccountRepository.delete(existingSocial) }
        verify { userRepository.save(any<User>()) }
        verify { socialAccountRepository.save(any<SocialAccount>()) }
        Assertions.assertEquals(2L, result.id)
    }

    // ───── helper ─────

    private fun User.withId(id: Long): User {
        val field = User::class.java.getDeclaredField("id")
        field.isAccessible = true
        field.set(this, id)
        return this
    }
}
