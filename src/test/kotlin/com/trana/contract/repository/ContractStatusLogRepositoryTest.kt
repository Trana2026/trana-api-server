package com.trana.contract.repository

import com.trana.contract.entity.ConsentType
import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractStatus
import com.trana.contract.entity.ContractStatusLog
import com.trana.contract.entity.DeliveryType
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
class ContractStatusLogRepositoryTest
    @Autowired
    constructor(
        private val contractRepository: ContractRepository,
        private val contractStatusLogRepository: ContractStatusLogRepository,
    ) {
        @Nested
        inner class FindAllByContractIdOrderByChangedAtAsc {
            @Test
            fun returnsLogsInChangedAtAsc() {
                val contract =
                    Contract.createDraft(
                        publicCode = "TST-LOG-001",
                        creatorUserId = 999_060L,
                        deliveryType = DeliveryType.DIRECT,
                        consentType = ConsentType.NONE,
                    )
                val contractId = contractRepository.save(contract).id!!

                contractStatusLogRepository.save(
                    ContractStatusLog.create(
                        contractId = contractId,
                        fromStatus = null,
                        toStatus = ContractStatus.IN_PROGRESS,
                        actorUserId = 999_060L,
                        reason = "create",
                    ),
                )
                Thread.sleep(10)
                contractStatusLogRepository.save(
                    ContractStatusLog.create(
                        contractId = contractId,
                        fromStatus = ContractStatus.IN_PROGRESS,
                        toStatus = ContractStatus.DRAFT,
                        actorUserId = 999_060L,
                        reason = "filled",
                    ),
                )
                Thread.sleep(10)
                contractStatusLogRepository.save(
                    ContractStatusLog.create(
                        contractId = contractId,
                        fromStatus = ContractStatus.DRAFT,
                        toStatus = ContractStatus.READY,
                        actorUserId = 999_060L,
                        reason = "markReady",
                    ),
                )

                val results = contractStatusLogRepository.findAllByContractIdOrderByChangedAtAsc(contractId)

                Assertions.assertEquals(3, results.size)
                Assertions.assertEquals(ContractStatus.IN_PROGRESS, results[0].toStatus)
                Assertions.assertEquals(ContractStatus.DRAFT, results[1].toStatus)
                Assertions.assertEquals(ContractStatus.READY, results[2].toStatus)
            }
        }
    }
