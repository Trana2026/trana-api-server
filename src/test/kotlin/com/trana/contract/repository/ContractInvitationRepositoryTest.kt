package com.trana.contract.repository

import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractInvitation
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ContractInvitationRepositoryTest
    @Autowired
    constructor(
        private val contractInvitationRepository: ContractInvitationRepository,
        private val contractRepository: ContractRepository,
    ) {
        /** V19 — 번호 미보유 계정(코드 공유) 대응: receiver_phone NULL INSERT 허용. */
        @Test
        fun persistsInvitationWithNullReceiverPhone() {
            val contractId =
                contractRepository
                    .save(Contract.createDraft(publicCode = "INV-NOPHONE-1", creatorUserId = 999_070L))
                    .id!!
            val invitation =
                ContractInvitation.create(
                    contractId = contractId,
                    token = "tok-nophone-001",
                    receiverName = "김테스트A",
                    receiverPhone = null,
                )

            val saved = contractInvitationRepository.saveAndFlush(invitation)

            Assertions.assertNotNull(saved.id)
            Assertions.assertNull(contractInvitationRepository.findByToken("tok-nophone-001")!!.receiverPhone)
        }
    }
