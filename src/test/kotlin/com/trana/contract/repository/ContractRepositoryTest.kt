package com.trana.contract.repository

import com.trana.contract.entity.ConsentType
import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractParty
import com.trana.contract.entity.ContractStatus
import com.trana.contract.entity.DeliveryType
import com.trana.contract.entity.PartyType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ContractRepositoryTest
    @Autowired
    constructor(
        private val contractRepository: ContractRepository,
        private val contractPartyRepository: ContractPartyRepository,
    ) {
        @Nested
        inner class FindByPublicCodeAndDeletedAtIsNull {
            @Test
            fun returnsContractWhenNotDeleted() {
                val contract =
                    Contract.createDraft(
                        publicCode = "TST-REPO-001",
                        creatorUserId = 1L,
                        deliveryType = DeliveryType.DIRECT,
                        consentType = ConsentType.NONE,
                    )
                contractRepository.save(contract)

                val found = contractRepository.findByPublicCodeAndDeletedAtIsNull("TST-REPO-001")

                Assertions.assertNotNull(found)
                Assertions.assertEquals("TST-REPO-001", found!!.publicCode)
            }

            @Test
            fun returnsNullWhenSoftDeleted() {
                val contract =
                    Contract.createDraft(
                        publicCode = "TST-REPO-002",
                        creatorUserId = 1L,
                        deliveryType = DeliveryType.DIRECT,
                        consentType = ConsentType.NONE,
                    )
                contract.softDelete()
                contractRepository.save(contract)

                val found = contractRepository.findByPublicCodeAndDeletedAtIsNull("TST-REPO-002")

                Assertions.assertNull(found)
            }
        }

        @Nested
        inner class ExistsByPublicCode {
            @Test
            fun returnsTrueWhenContractExists() {
                val contract =
                    Contract.createDraft(
                        publicCode = "TST-REPO-003",
                        creatorUserId = 1L,
                        deliveryType = DeliveryType.DIRECT,
                        consentType = ConsentType.NONE,
                    )
                contractRepository.save(contract)

                Assertions.assertTrue(contractRepository.existsByPublicCode("TST-REPO-003"))
            }

            @Test
            fun returnsFalseWhenAbsent() {
                Assertions.assertFalse(contractRepository.existsByPublicCode("TST-REPO-DOES-NOT-EXIST"))
            }
        }

        @Nested
        inner class FindAllByCreator {
            @Test
            fun returnsOnlyContractsCreatedByUser() {
                val mine =
                    Contract.createDraft(
                        publicCode = "TST-REPO-101",
                        creatorUserId = 999_001L,
                        deliveryType = DeliveryType.DIRECT,
                        consentType = ConsentType.NONE,
                    )
                val theirs =
                    Contract.createDraft(
                        publicCode = "TST-REPO-102",
                        creatorUserId = 999_002L,
                        deliveryType = DeliveryType.DIRECT,
                        consentType = ConsentType.NONE,
                    )
                contractRepository.saveAll(listOf(mine, theirs))

                val results = contractRepository.findAllByCreator(userId = 999_001L, status = null)

                Assertions.assertEquals(1, results.size)
                Assertions.assertEquals("TST-REPO-101", results[0].publicCode)
            }

            @Test
            fun filtersByStatus() {
                val inProgress =
                    Contract.createDraft(
                        publicCode = "TST-REPO-103",
                        creatorUserId = 999_010L,
                        deliveryType = DeliveryType.DIRECT,
                        consentType = ConsentType.NONE,
                    )
                val draft =
                    Contract.createDraft(
                        publicCode = "TST-REPO-104",
                        creatorUserId = 999_010L,
                        deliveryType = DeliveryType.DIRECT,
                        consentType = ConsentType.NONE,
                    )
                draft.updateDraft(
                    title = "아이폰",
                    price = 1_000_000L,
                    conditionSummary = "사용감 적음",
                    conditionDetails = "스크래치 1곳",
                    tradingPlatform = "당근마켓",
                )
                contractRepository.saveAll(listOf(inProgress, draft))

                val results = contractRepository.findAllByCreator(userId = 999_010L, status = ContractStatus.DRAFT)

                Assertions.assertEquals(1, results.size)
                Assertions.assertEquals("TST-REPO-104", results[0].publicCode)
            }

            @Test
            fun excludesSoftDeleted() {
                val active =
                    Contract.createDraft(
                        publicCode = "TST-REPO-105",
                        creatorUserId = 999_011L,
                        deliveryType = DeliveryType.DIRECT,
                        consentType = ConsentType.NONE,
                    )
                val deleted =
                    Contract.createDraft(
                        publicCode = "TST-REPO-106",
                        creatorUserId = 999_011L,
                        deliveryType = DeliveryType.DIRECT,
                        consentType = ConsentType.NONE,
                    )
                deleted.softDelete()
                contractRepository.saveAll(listOf(active, deleted))

                val results = contractRepository.findAllByCreator(userId = 999_011L, status = null)

                Assertions.assertEquals(1, results.size)
                Assertions.assertEquals("TST-REPO-105", results[0].publicCode)
            }
        }

        @Nested
        inner class FindAllByPartyUserId {
            @Test
            fun returnsContractsWhereUserIsParty() {
                val contract =
                    Contract.createDraft(
                        publicCode = "TST-REPO-201",
                        creatorUserId = 999_020L,
                        deliveryType = DeliveryType.DIRECT,
                        consentType = ConsentType.NONE,
                    )
                val saved = contractRepository.save(contract)
                val party =
                    ContractParty.create(
                        contractId = saved.id!!,
                        userId = 999_021L,
                        partyType = PartyType.BUYER,
                    )
                contractPartyRepository.save(party)

                val results =
                    contractRepository.findAllByPartyUserId(
                        userId = 999_021L,
                        status = null,
                        query = null,
                    )

                Assertions.assertEquals(1, results.size)
                Assertions.assertEquals("TST-REPO-201", results[0].publicCode)
            }

            @Test
            fun filtersByTitleQuery() {
                val iphone =
                    Contract.createDraft(
                        publicCode = "TST-REPO-202",
                        creatorUserId = 999_030L,
                        deliveryType = DeliveryType.DIRECT,
                        consentType = ConsentType.NONE,
                    )
                iphone.updateDraft(
                    title = "아이폰 15 Pro",
                    price = 1_000_000L,
                    conditionSummary = "사용감 적음",
                    conditionDetails = "스크래치 1곳",
                    tradingPlatform = "당근마켓",
                )
                val galaxy =
                    Contract.createDraft(
                        publicCode = "TST-REPO-203",
                        creatorUserId = 999_030L,
                        deliveryType = DeliveryType.DIRECT,
                        consentType = ConsentType.NONE,
                    )
                galaxy.updateDraft(
                    title = "갤럭시 S24",
                    price = 800_000L,
                    conditionSummary = "사용감 적음",
                    conditionDetails = "스크래치 1곳",
                    tradingPlatform = "번개장터",
                )
                contractRepository.saveAll(listOf(iphone, galaxy))

                val results =
                    contractRepository.findAllByPartyUserId(
                        userId = 999_030L,
                        status = null,
                        query = "아이폰",
                    )

                Assertions.assertEquals(1, results.size)
                Assertions.assertEquals("TST-REPO-202", results[0].publicCode)
            }

            @Test
            fun excludesInProgressWhenQueryProvided() {
                val inProgress =
                    Contract.createDraft(
                        publicCode = "TST-REPO-204",
                        creatorUserId = 999_040L,
                        deliveryType = DeliveryType.DIRECT,
                        consentType = ConsentType.NONE,
                    )
                val titled =
                    Contract.createDraft(
                        publicCode = "TST-REPO-205",
                        creatorUserId = 999_040L,
                        deliveryType = DeliveryType.DIRECT,
                        consentType = ConsentType.NONE,
                    )
                titled.updateDraft(
                    title = "아이폰 15 Pro",
                    price = 1_000_000L,
                    conditionSummary = "사용감 적음",
                    conditionDetails = "스크래치 1곳",
                    tradingPlatform = "당근마켓",
                )
                contractRepository.saveAll(listOf(inProgress, titled))

                val results =
                    contractRepository.findAllByPartyUserId(
                        userId = 999_040L,
                        status = null,
                        query = "아이폰",
                    )

                Assertions.assertEquals(1, results.size)
                Assertions.assertEquals("TST-REPO-205", results[0].publicCode)
            }
        }
    }
