package com.trana.contract.service

import com.trana.contract.entity.ContractStatusLog
import com.trana.contract.repository.ContractStatusLogRepository
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 계약 상태 전이 WORM 로그 listener.
 *
 * @EventListener (synchronous) — Service 의 @Transactional 안에서 함께 INSERT.
 * status 변경 + log INSERT 가 원자적 (둘 다 성공 or 둘 다 rollback).
 *
 * cf. AiExtractionAsyncProcessor 는 OpenAI 호출 분리용 AFTER_COMMIT + @Async.
 * 여기는 빠른 DB INSERT 1회 → 동기 처리가 적절.
 */
@Component
class ContractStatusLogListener(
    private val repository: ContractStatusLogRepository,
) {
    @EventListener
    fun handle(event: ContractStatusChangedEvent) {
        val log =
            ContractStatusLog.create(
                contractId = event.contractId,
                fromStatus = event.fromStatus,
                toStatus = event.toStatus,
                actorUserId = event.actorUserId,
                reason = event.reason,
            )
        repository.save(log)
    }
}
