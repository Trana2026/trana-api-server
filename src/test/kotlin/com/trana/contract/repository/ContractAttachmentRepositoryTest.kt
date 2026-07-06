package com.trana.contract.repository

import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractAttachment
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
class ContractAttachmentRepositoryTest
    @Autowired
    constructor(
        private val contractRepository: ContractRepository,
        private val contractAttachmentRepository: ContractAttachmentRepository,
    ) {
        @Nested
        inner class FindAllByContractIdOrderBySortOrderAsc {
            @Test
            fun returnsAttachmentsInSortOrderAsc() {
                val contract =
                    Contract.createDraft(
                        publicCode = "TST-ATT-001",
                        creatorUserId = 999_050L,
                    )
                val contractId = contractRepository.save(contract).id!!

                val a =
                    ContractAttachment.create(
                        contractId = contractId,
                        s3Key = "att-a",
                        originalFilename = "a.jpg",
                        contentType = "image/jpeg",
                        sizeBytes = 1000L,
                        sha256 = "hash-a",
                        sortOrder = 2,
                    )
                val b =
                    ContractAttachment.create(
                        contractId = contractId,
                        s3Key = "att-b",
                        originalFilename = "b.jpg",
                        contentType = "image/jpeg",
                        sizeBytes = 1000L,
                        sha256 = "hash-b",
                        sortOrder = 0,
                    )
                val c =
                    ContractAttachment.create(
                        contractId = contractId,
                        s3Key = "att-c",
                        originalFilename = "c.jpg",
                        contentType = "image/jpeg",
                        sizeBytes = 1000L,
                        sha256 = "hash-c",
                        sortOrder = 1,
                    )
                contractAttachmentRepository.saveAll(listOf(a, b, c))

                val results = contractAttachmentRepository.findAllByContractIdOrderBySortOrderAsc(contractId)

                Assertions.assertEquals(3, results.size)
                Assertions.assertEquals("att-b", results[0].s3Key)
                Assertions.assertEquals("att-c", results[1].s3Key)
                Assertions.assertEquals("att-a", results[2].s3Key)
            }
        }
    }
