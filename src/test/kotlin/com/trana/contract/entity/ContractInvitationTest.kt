package com.trana.contract.entity

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class ContractInvitationTest {
    @Nested
    inner class Create {
        @Test
        fun initializesWithDefaultTtl() {
            val invitation =
                ContractInvitation.create(
                    contractId = 1L,
                    token = "test-token-123",
                    receiverName = "홍길동",
                    receiverPhone = "010-1234-5678",
                )

            Assertions.assertEquals(1L, invitation.contractId)
            Assertions.assertEquals("test-token-123", invitation.token)
            Assertions.assertEquals("홍길동", invitation.receiverName)
            Assertions.assertEquals("010-1234-5678", invitation.receiverPhone)
            Assertions.assertNull(invitation.usedAt)
            Assertions.assertNull(invitation.acceptedByUserId)

            val expectedExpiry = Instant.now().plus(Duration.ofDays(7))
            val drift = Duration.between(invitation.expiresAt, expectedExpiry).abs()
            Assertions.assertTrue(
                drift < Duration.ofSeconds(2),
                "expiresAt 가 now+7일과 2초 이내여야 함 (실제 drift=$drift)",
            )
        }

        @Test
        fun respectsCustomTtl() {
            val customTtl = Duration.ofHours(1)
            val invitation =
                ContractInvitation.create(
                    contractId = 1L,
                    token = "test-token-456",
                    receiverName = "홍길동",
                    receiverPhone = "010-1234-5678",
                    ttl = customTtl,
                )

            val expectedExpiry = Instant.now().plus(customTtl)
            val drift = Duration.between(invitation.expiresAt, expectedExpiry).abs()
            Assertions.assertTrue(
                drift < Duration.ofSeconds(2),
                "expiresAt 가 now+1시간과 2초 이내여야 함 (실제 drift=$drift)",
            )
        }
    }

    @Nested
    inner class IsExpired {
        @Test
        fun returnsTrueWhenNowAfterExpiresAt() {
            val invitation =
                ContractInvitation.create(
                    contractId = 1L,
                    token = "test-token",
                    receiverName = "홍길동",
                    receiverPhone = "010-0000-0000",
                    ttl = Duration.ofHours(1),
                )
            val futureNow = invitation.expiresAt.plus(Duration.ofSeconds(1))

            Assertions.assertTrue(invitation.isExpired(futureNow))
        }

        @Test
        fun returnsFalseWhenNowBeforeExpiresAt() {
            val invitation =
                ContractInvitation.create(
                    contractId = 1L,
                    token = "test-token",
                    receiverName = "홍길동",
                    receiverPhone = "010-0000-0000",
                    ttl = Duration.ofHours(1),
                )
            val pastNow = invitation.expiresAt.minus(Duration.ofSeconds(1))

            Assertions.assertFalse(invitation.isExpired(pastNow))
        }

        @Test
        fun returnsTrueAtExpiresAtBoundary() {
            val invitation =
                ContractInvitation.create(
                    contractId = 1L,
                    token = "test-token",
                    receiverName = "홍길동",
                    receiverPhone = "010-0000-0000",
                    ttl = Duration.ofHours(1),
                )

            Assertions.assertTrue(invitation.isExpired(invitation.expiresAt))
        }
    }

    @Nested
    inner class IsActive {
        @Test
        fun returnsTrueWhenUnusedAndNotExpired() {
            val invitation =
                ContractInvitation.create(
                    contractId = 1L,
                    token = "test-token",
                    receiverName = "홍길동",
                    receiverPhone = "010-0000-0000",
                    ttl = Duration.ofHours(1),
                )
            val pastNow = invitation.expiresAt.minus(Duration.ofSeconds(1))

            Assertions.assertTrue(invitation.isActive(pastNow))
        }

        @Test
        fun returnsFalseWhenAlreadyUsed() {
            val invitation =
                ContractInvitation.create(
                    contractId = 1L,
                    token = "test-token",
                    receiverName = "홍길동",
                    receiverPhone = "010-0000-0000",
                    ttl = Duration.ofHours(1),
                )
            invitation.markUsed(acceptedByUserId = 42L)
            val pastNow = invitation.expiresAt.minus(Duration.ofSeconds(1))

            Assertions.assertFalse(invitation.isActive(pastNow))
        }

        @Test
        fun returnsFalseWhenExpired() {
            val invitation =
                ContractInvitation.create(
                    contractId = 1L,
                    token = "test-token",
                    receiverName = "홍길동",
                    receiverPhone = "010-0000-0000",
                    ttl = Duration.ofHours(1),
                )
            val futureNow = invitation.expiresAt.plus(Duration.ofSeconds(1))

            Assertions.assertFalse(invitation.isActive(futureNow))
        }
    }

    @Nested
    inner class MarkUsed {
        @Test
        fun stampsUsedAtAndAcceptedByUserId() {
            val invitation =
                ContractInvitation.create(
                    contractId = 1L,
                    token = "test-token",
                    receiverName = "홍길동",
                    receiverPhone = "010-0000-0000",
                )

            invitation.markUsed(acceptedByUserId = 42L)

            Assertions.assertNotNull(invitation.usedAt)
            Assertions.assertEquals(42L, invitation.acceptedByUserId)
        }

        @Test
        fun throwsWhenAlreadyUsed() {
            val invitation =
                ContractInvitation.create(
                    contractId = 1L,
                    token = "test-token",
                    receiverName = "홍길동",
                    receiverPhone = "010-0000-0000",
                )
            invitation.markUsed(acceptedByUserId = 42L)

            Assertions.assertThrows(IllegalStateException::class.java) {
                invitation.markUsed(acceptedByUserId = 43L)
            }
        }

        @Test
        fun throwsWhenExpired() {
            val invitation =
                ContractInvitation.create(
                    contractId = 1L,
                    token = "test-token",
                    receiverName = "홍길동",
                    receiverPhone = "010-0000-0000",
                    ttl = Duration.ZERO,
                )

            Assertions.assertThrows(IllegalStateException::class.java) {
                invitation.markUsed(acceptedByUserId = 42L)
            }
        }
    }
}
