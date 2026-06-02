package com.trana.contract.entity

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ContractPartyTest {
    @Nested
    inner class Create {
        @Test
        fun initializesUnvalidatedAndIncomplete() {
            val party =
                ContractParty.create(
                    contractId = 1L,
                    userId = 42L,
                    partyType = PartyType.SELLER,
                )

            Assertions.assertEquals(1L, party.contractId)
            Assertions.assertEquals(42L, party.userId)
            Assertions.assertEquals(PartyType.SELLER, party.partyType)
            Assertions.assertFalse(party.validated)
            Assertions.assertNull(party.validatedAt)
            Assertions.assertNull(party.completedAt)
        }
    }

    @Nested
    inner class MarkValidated {
        @Test
        fun setsValidatedAndTimestamp() {
            val party =
                ContractParty.create(
                    contractId = 1L,
                    userId = 42L,
                    partyType = PartyType.SELLER,
                )

            party.markValidated()

            Assertions.assertTrue(party.validated)
            Assertions.assertNotNull(party.validatedAt)
        }

        @Test
        fun throwsWhenAlreadyValidated() {
            val party =
                ContractParty.create(
                    contractId = 1L,
                    userId = 42L,
                    partyType = PartyType.SELLER,
                )
            party.markValidated()

            Assertions.assertThrows(IllegalStateException::class.java) {
                party.markValidated()
            }
        }
    }

    @Nested
    inner class MarkCompleted {
        @Test
        fun stampsCompletedAt() {
            val party =
                ContractParty.create(
                    contractId = 1L,
                    userId = 42L,
                    partyType = PartyType.SELLER,
                )

            party.markCompleted()

            Assertions.assertNotNull(party.completedAt)
        }

        @Test
        fun throwsWhenAlreadyCompleted() {
            val party =
                ContractParty.create(
                    contractId = 1L,
                    userId = 42L,
                    partyType = PartyType.SELLER,
                )
            party.markCompleted()

            Assertions.assertThrows(IllegalStateException::class.java) {
                party.markCompleted()
            }
        }
    }
}
