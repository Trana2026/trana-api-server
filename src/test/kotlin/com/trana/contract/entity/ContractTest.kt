package com.trana.contract.entity

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ContractTest {
    private fun draftContract(
        publicCode: String = "TST-CTR-FIX",
        consentType: ConsentType = ConsentType.NONE,
    ): Contract {
        val contract =
            Contract.createDraft(
                publicCode = publicCode,
                creatorUserId = 1L,
                deliveryType = DeliveryType.DIRECT,
                consentType = consentType,
            )
        contract.updateDraft(
            title = "아이폰 15 Pro",
            price = 1_200_000L,
            conditionSummary = "사용감 적음",
            conditionDetails = "스크래치 1곳",
            tradingPlatform = "당근마켓",
        )
        return contract
    }

    private fun sharedContract(publicCode: String = "TST-CTR-FIX-SHARED"): Contract {
        val contract = draftContract(publicCode)
        contract.markReady(pdfS3Key = "contracts/$publicCode/v1.pdf", pdfSha256 = "hash-v1")
        contract.markShared()
        return contract
    }

    private fun receiverSignedContract(publicCode: String = "TST-CTR-FIX-RECV"): Contract {
        val contract = sharedContract(publicCode)
        contract.markReceiverSigned(pdfS3Key = "contracts/$publicCode/v2.pdf", pdfSha256 = "hash-v2")
        return contract
    }

    @Nested
    inner class CreateDraft {
        @Test
        fun initializesAsInProgress() {
            val contract =
                Contract.createDraft(
                    publicCode = "TST-CTR-001",
                    creatorUserId = 1L,
                    deliveryType = DeliveryType.DIRECT,
                    consentType = ConsentType.NONE,
                )

            Assertions.assertEquals(ContractStatus.IN_PROGRESS, contract.status)
            Assertions.assertEquals(DisputeState.NONE, contract.disputeState)
            Assertions.assertNull(contract.guardianId)
            Assertions.assertNull(contract.guardianConsentAt)
            Assertions.assertEquals(0, contract.version)
            Assertions.assertNull(contract.pdfS3Key)
            Assertions.assertNull(contract.contentHash)
            Assertions.assertNull(contract.pdfGeneratedAt)
            Assertions.assertNull(contract.completedAt)
            Assertions.assertNull(contract.deletedAt)
            Assertions.assertEquals(3, contract.warrantyPeriodDays)

            Assertions.assertEquals("TST-CTR-001", contract.publicCode)
            Assertions.assertEquals(1L, contract.creatorUserId)
            Assertions.assertEquals(DeliveryType.DIRECT, contract.deliveryType)
            Assertions.assertEquals(ConsentType.NONE, contract.consentType)
        }
    }

    @Nested
    inner class UpdateDraft {
        @Test
        fun fillingAllRequiredFieldsTransitionsToDraft() {
            val contract =
                Contract.createDraft(
                    publicCode = "TST-CTR-002",
                    creatorUserId = 1L,
                    deliveryType = DeliveryType.DIRECT,
                    consentType = ConsentType.NONE,
                )

            contract.updateDraft(
                title = "아이폰 15 Pro 256GB 블랙",
                price = 1_200_000L,
                conditionSummary = "사용감 적음",
                conditionDetails = "스크래치 1곳, 박스 포함",
                tradingPlatform = "당근마켓",
            )

            Assertions.assertEquals(ContractStatus.DRAFT, contract.status)
            Assertions.assertEquals("아이폰 15 Pro 256GB 블랙", contract.title)
            Assertions.assertEquals(1_200_000L, contract.price)
            Assertions.assertEquals("사용감 적음", contract.conditionSummary)
            Assertions.assertEquals("스크래치 1곳, 박스 포함", contract.conditionDetails)
            Assertions.assertEquals("당근마켓", contract.tradingPlatform)
        }

        @Test
        fun withMissingFieldStaysInProgress() {
            val contract =
                Contract.createDraft(
                    publicCode = "TST-CTR-003",
                    creatorUserId = 1L,
                    deliveryType = DeliveryType.DIRECT,
                    consentType = ConsentType.NONE,
                )

            contract.updateDraft(
                title = "아이폰 15 Pro 256GB 블랙",
                price = 1_200_000L,
                conditionSummary = "사용감 적음",
                conditionDetails = "스크래치 1곳, 박스 포함",
                // tradingPlatform 생략 — null 유지
            )

            Assertions.assertEquals(ContractStatus.IN_PROGRESS, contract.status)
            Assertions.assertEquals("아이폰 15 Pro 256GB 블랙", contract.title)
            Assertions.assertEquals(1_200_000L, contract.price)
            Assertions.assertEquals("사용감 적음", contract.conditionSummary)
            Assertions.assertEquals("스크래치 1곳, 박스 포함", contract.conditionDetails)
            Assertions.assertNull(contract.tradingPlatform)
        }

        @Test
        fun addingMissingFieldLaterTransitionsToDraft() {
            val contract =
                Contract.createDraft(
                    publicCode = "TST-CTR-004",
                    creatorUserId = 1L,
                    deliveryType = DeliveryType.DIRECT,
                    consentType = ConsentType.NONE,
                )

            contract.updateDraft(
                title = "아이폰 15 Pro 256GB 블랙",
                price = 1_200_000L,
                conditionSummary = "사용감 적음",
                conditionDetails = "스크래치 1곳, 박스 포함",
            )
            Assertions.assertEquals(ContractStatus.IN_PROGRESS, contract.status)

            contract.updateDraft(tradingPlatform = "당근마켓")

            Assertions.assertEquals(ContractStatus.DRAFT, contract.status)
            Assertions.assertEquals("아이폰 15 Pro 256GB 블랙", contract.title)
            Assertions.assertEquals(1_200_000L, contract.price)
            Assertions.assertEquals("사용감 적음", contract.conditionSummary)
            Assertions.assertEquals("스크래치 1곳, 박스 포함", contract.conditionDetails)
            Assertions.assertEquals("당근마켓", contract.tradingPlatform)
        }

        @Test
        fun throwsWhenCalledAfterShared() {
            val contract =
                Contract.createDraft(
                    publicCode = "TST-CTR-005",
                    creatorUserId = 1L,
                    deliveryType = DeliveryType.DIRECT,
                    consentType = ConsentType.NONE,
                )
            contract.updateDraft(
                title = "아이폰 15 Pro",
                price = 1_200_000L,
                conditionSummary = "사용감 적음",
                conditionDetails = "스크래치 1곳",
                tradingPlatform = "당근마켓",
            )
            contract.markReady(pdfS3Key = "contracts/TST-CTR-005/v1.pdf", pdfSha256 = "fake-sha")
            contract.markShared()

            Assertions.assertThrows(IllegalStateException::class.java) {
                contract.updateDraft(title = "변경 시도")
            }
        }

        @Test
        fun throwsWhenSoftDeleted() {
            val contract =
                Contract.createDraft(
                    publicCode = "TST-CTR-006",
                    creatorUserId = 1L,
                    deliveryType = DeliveryType.DIRECT,
                    consentType = ConsentType.NONE,
                )
            contract.softDelete()

            Assertions.assertThrows(IllegalStateException::class.java) {
                contract.updateDraft(title = "변경 시도")
            }
        }
    }

    @Nested
    inner class MarkReady {
        @Test
        fun transitionsToReadyWithPdfMetadata() {
            val contract = draftContract("TST-CTR-101")

            contract.markReady(
                pdfS3Key = "contracts/TST-CTR-101/v1.pdf",
                pdfSha256 = "fake-sha-256",
            )

            Assertions.assertEquals(ContractStatus.READY, contract.status)
            Assertions.assertEquals("contracts/TST-CTR-101/v1.pdf", contract.pdfS3Key)
            Assertions.assertEquals("fake-sha-256", contract.contentHash)
            Assertions.assertNotNull(contract.pdfGeneratedAt)
            Assertions.assertEquals(1, contract.version)
        }

        @Test
        fun throwsWhenCalledOnInProgress() {
            val contract =
                Contract.createDraft(
                    publicCode = "TST-CTR-102",
                    creatorUserId = 1L,
                    deliveryType = DeliveryType.DIRECT,
                    consentType = ConsentType.NONE,
                )

            Assertions.assertThrows(IllegalStateException::class.java) {
                contract.markReady(pdfS3Key = "fake-key", pdfSha256 = "fake-sha")
            }
        }

        @Test
        fun throwsWhenGuardianRequiredButNotConsented() {
            val contract =
                draftContract(
                    publicCode = "TST-CTR-103",
                    consentType = ConsentType.GUARDIAN_REQUIRED,
                )

            Assertions.assertThrows(IllegalStateException::class.java) {
                contract.markReady(pdfS3Key = "fake-key", pdfSha256 = "fake-sha")
            }
        }

        @Test
        fun allowsAfterGuardianConsented() {
            val contract =
                draftContract(
                    publicCode = "TST-CTR-104",
                    consentType = ConsentType.GUARDIAN_REQUIRED,
                )
            contract.markGuardianConsented(boundGuardianId = 42L)

            contract.markReady(
                pdfS3Key = "contracts/TST-CTR-104/v1.pdf",
                pdfSha256 = "fake-sha",
            )

            Assertions.assertEquals(ContractStatus.READY, contract.status)
            Assertions.assertEquals(42L, contract.guardianId)
            Assertions.assertNotNull(contract.guardianConsentAt)
        }
    }

    @Nested
    inner class MarkGuardianConsented {
        @Test
        fun setsGuardianIdAndConsentTimestamp() {
            val contract =
                Contract.createDraft(
                    publicCode = "TST-CTR-201",
                    creatorUserId = 1L,
                    deliveryType = DeliveryType.DIRECT,
                    consentType = ConsentType.GUARDIAN_REQUIRED,
                )

            contract.markGuardianConsented(boundGuardianId = 99L)

            Assertions.assertEquals(99L, contract.guardianId)
            Assertions.assertNotNull(contract.guardianConsentAt)
        }

        @Test
        fun throwsWhenConsentTypeIsNone() {
            val contract =
                Contract.createDraft(
                    publicCode = "TST-CTR-202",
                    creatorUserId = 1L,
                    deliveryType = DeliveryType.DIRECT,
                    consentType = ConsentType.NONE,
                )

            Assertions.assertThrows(IllegalStateException::class.java) {
                contract.markGuardianConsented(boundGuardianId = 99L)
            }
        }

        @Test
        fun throwsWhenAlreadyConsented() {
            val contract =
                Contract.createDraft(
                    publicCode = "TST-CTR-203",
                    creatorUserId = 1L,
                    deliveryType = DeliveryType.DIRECT,
                    consentType = ConsentType.GUARDIAN_REQUIRED,
                )
            contract.markGuardianConsented(boundGuardianId = 99L)

            Assertions.assertThrows(IllegalStateException::class.java) {
                contract.markGuardianConsented(boundGuardianId = 100L)
            }
        }
    }

    @Nested
    inner class MarkShared {
        @Test
        fun transitionsFromReadyToShared() {
            val contract = draftContract("TST-CTR-301")
            contract.markReady(pdfS3Key = "fake-key", pdfSha256 = "fake-sha")

            contract.markShared()

            Assertions.assertEquals(ContractStatus.SHARED, contract.status)
        }

        @Test
        fun throwsWhenCalledOnDraft() {
            val contract = draftContract("TST-CTR-302")

            Assertions.assertThrows(IllegalStateException::class.java) {
                contract.markShared()
            }
        }
    }

    @Nested
    inner class MarkReceiverSigned {
        @Test
        fun transitionsFromSharedAndUpdatesPdfMetadata() {
            val contract = draftContract("TST-CTR-401")
            contract.markReady(pdfS3Key = "contracts/TST-CTR-401/v1.pdf", pdfSha256 = "hash-v1")
            contract.markShared()

            contract.markReceiverSigned(
                pdfS3Key = "contracts/TST-CTR-401/v2.pdf",
                pdfSha256 = "hash-v2",
            )

            Assertions.assertEquals(ContractStatus.RECEIVER_SIGNED, contract.status)
            Assertions.assertEquals("contracts/TST-CTR-401/v2.pdf", contract.pdfS3Key)
            Assertions.assertEquals("hash-v2", contract.contentHash)
            Assertions.assertNotNull(contract.pdfGeneratedAt)
        }

        @Test
        fun throwsWhenCalledOnReady() {
            val contract = draftContract("TST-CTR-402")
            contract.markReady(pdfS3Key = "fake-key", pdfSha256 = "fake-sha")

            Assertions.assertThrows(IllegalStateException::class.java) {
                contract.markReceiverSigned(pdfS3Key = "v2-key", pdfSha256 = "v2-hash")
            }
        }
    }

    @Nested
    inner class MarkSigned {
        @Test
        fun transitionsFromReceiverSignedAndUpdatesPdfMetadata() {
            val contract = receiverSignedContract("TST-CTR-501")

            contract.markSigned(
                pdfS3Key = "contracts/TST-CTR-501/v3.pdf",
                pdfSha256 = "hash-v3",
            )

            Assertions.assertEquals(ContractStatus.SIGNED, contract.status)
            Assertions.assertEquals("contracts/TST-CTR-501/v3.pdf", contract.pdfS3Key)
            Assertions.assertEquals("hash-v3", contract.contentHash)
            Assertions.assertNotNull(contract.pdfGeneratedAt)
        }

        @Test
        fun throwsWhenCalledOnShared() {
            val contract = sharedContract("TST-CTR-502")

            Assertions.assertThrows(IllegalStateException::class.java) {
                contract.markSigned(pdfS3Key = "v3-key", pdfSha256 = "v3-hash")
            }
        }
    }

    @Nested
    inner class MarkCompleted {
        @Test
        fun transitionsFromSignedAndStampsCompletedAt() {
            val contract = receiverSignedContract("TST-CTR-601")
            contract.markSigned(pdfS3Key = "v3-key", pdfSha256 = "v3-hash")

            contract.markCompleted()

            Assertions.assertEquals(ContractStatus.COMPLETED, contract.status)
            Assertions.assertNotNull(contract.completedAt)
        }

        @Test
        fun throwsWhenCalledOnReceiverSigned() {
            val contract = receiverSignedContract("TST-CTR-602")

            Assertions.assertThrows(IllegalStateException::class.java) {
                contract.markCompleted()
            }
        }
    }

    @Nested
    inner class MarkRevisionRequested {
        @Test
        fun transitionsFromSharedToRevisionRequested() {
            val contract = sharedContract("TST-CTR-701")

            contract.markRevisionRequested()

            Assertions.assertEquals(ContractStatus.REVISION_REQUESTED, contract.status)
        }

        @Test
        fun throwsWhenCalledOnReceiverSigned() {
            val contract = receiverSignedContract("TST-CTR-702")

            Assertions.assertThrows(IllegalStateException::class.java) {
                contract.markRevisionRequested()
            }
        }
    }

    @Nested
    inner class MarkRevertToDraft {
        @Test
        fun revertsFromReadyAndClearsPdfMetadata() {
            val contract = draftContract("TST-CTR-801")
            contract.markReady(pdfS3Key = "v1-key", pdfSha256 = "v1-hash")

            contract.markRevertToDraft()

            Assertions.assertEquals(ContractStatus.DRAFT, contract.status)
            Assertions.assertNull(contract.pdfS3Key)
            Assertions.assertNull(contract.contentHash)
            Assertions.assertNull(contract.pdfGeneratedAt)
        }

        @Test
        fun revertsFromRevisionRequested() {
            val contract = sharedContract("TST-CTR-802")
            contract.markRevisionRequested()

            contract.markRevertToDraft()

            Assertions.assertEquals(ContractStatus.DRAFT, contract.status)
            Assertions.assertNull(contract.pdfS3Key)
            Assertions.assertNull(contract.contentHash)
            Assertions.assertNull(contract.pdfGeneratedAt)
        }

        @Test
        fun throwsWhenCalledOnReceiverSigned() {
            val contract = receiverSignedContract("TST-CTR-803")

            Assertions.assertThrows(IllegalStateException::class.java) {
                contract.markRevertToDraft()
            }
        }
    }

    @Nested
    inner class SoftDelete {
        @Test
        fun stampsDeletedAtOnDraft() {
            val contract = draftContract("TST-CTR-901")

            contract.softDelete()

            Assertions.assertEquals(ContractStatus.DRAFT, contract.status)
            Assertions.assertNotNull(contract.deletedAt)
        }

        @Test
        fun throwsWhenCalledOnReady() {
            val contract = draftContract("TST-CTR-902")
            contract.markReady(pdfS3Key = "v1-key", pdfSha256 = "v1-hash")

            Assertions.assertThrows(IllegalStateException::class.java) {
                contract.softDelete()
            }
        }

        @Test
        fun throwsWhenAlreadyDeleted() {
            val contract = draftContract("TST-CTR-903")
            contract.softDelete()

            Assertions.assertThrows(IllegalStateException::class.java) {
                contract.softDelete()
            }
        }
    }
}
