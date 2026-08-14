package com.trana.contract.service

import com.trana.contract.ContractException
import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractParty
import com.trana.contract.entity.ContractStatus
import com.trana.contract.repository.ContractPartyRepository
import com.trana.contract.repository.ContractRepository
import com.trana.user.service.UserBlockService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * 차단 가시성 필터 검증 — 차단한 상대(creator)가 만든 미서명 계약은 숨김,
 * 양측 서명 완료(SIGNED/COMPLETED)는 항상 노출.
 */
class ContractAccessGuardBlockTest {
    private val contractRepository: ContractRepository = mockk()
    private val contractPartyRepository: ContractPartyRepository = mockk()
    private val userBlockService: UserBlockService = mockk()

    private val guard = ContractAccessGuard(contractRepository, contractPartyRepository, userBlockService)

    private fun contract(status: ContractStatus): Contract =
        mockk(relaxed = true) {
            every { id } returns CONTRACT_ID
            every { creatorUserId } returns BLOCKED_CREATOR
            every { publicCode } returns "PUB-1"
            every { this@mockk.status } returns status
        }

    @Test
    fun hidesSharedContractCreatedByBlockedUser() {
        every { userBlockService.blockedCreatorIds(VIEWER) } returns setOf(BLOCKED_CREATOR)

        Assertions.assertTrue(guard.isHiddenByBlock(contract(ContractStatus.SHARED), VIEWER))
    }

    @Test
    fun alwaysShowsSignedAndCompletedEvenIfCreatorBlocked() {
        every { userBlockService.blockedCreatorIds(VIEWER) } returns setOf(BLOCKED_CREATOR)

        Assertions.assertFalse(guard.isHiddenByBlock(contract(ContractStatus.SIGNED), VIEWER))
        Assertions.assertFalse(guard.isHiddenByBlock(contract(ContractStatus.COMPLETED), VIEWER))
    }

    @Test
    fun showsContractWhenCreatorNotBlocked() {
        every { userBlockService.blockedCreatorIds(VIEWER) } returns emptySet()

        Assertions.assertFalse(guard.isHiddenByBlock(contract(ContractStatus.SHARED), VIEWER))
    }

    @Test
    fun loadAccessibleThrowsNotFoundForHiddenContract() {
        val c = contract(ContractStatus.SHARED)
        every { contractRepository.findByPublicCodeAndDeletedAtIsNull("PUB-1") } returns c
        every { contractPartyRepository.findFirstByContractIdAndUserId(CONTRACT_ID, VIEWER) } returns
            mockk<ContractParty>(relaxed = true)
        every { userBlockService.blockedCreatorIds(VIEWER) } returns setOf(BLOCKED_CREATOR)

        Assertions.assertThrows(ContractException.NotFound::class.java) {
            guard.loadAccessible("PUB-1", VIEWER)
        }
    }

    @Test
    fun loadAccessibleReturnsSignedContractEvenIfCreatorBlocked() {
        val c = contract(ContractStatus.SIGNED)
        every { contractRepository.findByPublicCodeAndDeletedAtIsNull("PUB-1") } returns c
        every { contractPartyRepository.findFirstByContractIdAndUserId(CONTRACT_ID, VIEWER) } returns
            mockk<ContractParty>(relaxed = true)
        every { userBlockService.blockedCreatorIds(VIEWER) } returns setOf(BLOCKED_CREATOR)

        Assertions.assertSame(c, guard.loadAccessible("PUB-1", VIEWER))
    }

    companion object {
        private const val VIEWER = 100L
        private const val BLOCKED_CREATOR = 200L
        private const val CONTRACT_ID = 500L
    }
}
