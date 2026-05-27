package com.trana.contract.service

import com.trana.contract.entity.ContractStatus

/**
 * 계약 상태 전이 이벤트 — Service 가 Entity 메서드 호출 후 publish.
 *
 * ContractStatusLogListener 가 받아서 contract_status_logs INSERT.
 *
 * - fromStatus = null: INITIAL (계약 생성 시점)
 * - actorUserId = null: 시스템 자동 전이 (예: 양측 서명 완료 → SIGNED 자동)
 */
data class ContractStatusChangedEvent(
    val contractId: Long,
    val fromStatus: ContractStatus?,
    val toStatus: ContractStatus,
    val actorUserId: Long?,
    val reason: String?,
)
