package com.trana.contract.repository

import com.trana.contract.entity.ContractInvitation
import org.springframework.data.jpa.repository.JpaRepository

interface ContractInvitationRepository : JpaRepository<ContractInvitation, Long> {
    fun findByToken(token: String): ContractInvitation?

    fun findFirstByContractIdOrderByIdDesc(contractId: Long): ContractInvitation?
}
