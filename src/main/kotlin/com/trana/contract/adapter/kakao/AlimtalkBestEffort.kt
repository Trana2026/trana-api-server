package com.trana.contract.adapter.kakao

import org.slf4j.LoggerFactory

private val alimtalkBestEffortLog = LoggerFactory.getLogger("com.trana.alimtalk.BestEffort")

/**
 * 알림톡 발송을 best-effort 로 실행 — 발송 실패([AligoSendException])가
 * 계약 취소/서명 등 핵심 도메인 트랜잭션을 롤백시키지 않도록 삼키고 로그만 남긴다.
 *
 * 알림톡은 부가 통지라 실패해도 계약 상태 전이는 유지되어야 한다.
 * AligoSendException 만 잡으므로 로직 오류(NPE 등)나 Mock 은 그대로 전파.
 * 실패 원인은 [AligoSendException] 자체 로그 + 여기 warn 으로 남는다.
 */
fun sendAlimtalkBestEffort(
    label: String,
    block: () -> Unit,
) {
    try {
        block()
    } catch (e: AligoSendException) {
        alimtalkBestEffortLog.warn(
            "[ALIMTALK] {} 발송 실패(무시) — code={} msg={}",
            label,
            e.code,
            e.responseMessage,
        )
    }
}
