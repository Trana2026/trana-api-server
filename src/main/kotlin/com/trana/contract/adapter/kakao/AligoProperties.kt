package com.trana.contract.adapter.kakao

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 알리고 카카오 알림톡 API 자격증명 + 템플릿 매핑.
 *
 * - 자격증명 (apiKey/userId/senderKey/sender): env 만 (secret)
 * - tplCode 4종: yml 직접 (사전 등록 + 심사 통과한 카톡 BSP 템플릿 ID — 변경 시 BSP 재심사)
 *
 * 환경변수 명:
 * - `TRANA_ALIGO_API_KEY`
 * - `TRANA_ALIGO_USER_ID`
 * - `TRANA_ALIGO_SENDER_KEY`
 * - `TRANA_ALIGO_SENDER` (발신번호, 알리고 콘솔 등록)
 *
 * 운영 정책:
 * - local: application-local.yml 직접 박음 (gitignore)
 * - dev/prod: Railway env
 */
@ConfigurationProperties(prefix = "trana.alimtalk.aligo")
data class AligoProperties(
    val apiKey: String,
    val userId: String,
    val senderKey: String,
    val sender: String,
    val tplCode: TplCode,
    /** true 면 알리고 `testmode=Y` (응답 0 반환하지만 실제 발송 X). 운영 사고 방지용 dry-run. */
    val testMode: Boolean = false,
) {
    data class TplCode(
        /** UI_4032 — SHARED 전이 시 수신자에게 (1차 서명 요청) */
        val newContract: String,
        /** UI_4033 — RECEIVER_SIGNED 전이 시 생성자에게 (최종 서명 요청) */
        val receiverSigned: String,
        /** UI_4034 — REVISION_REQUESTED 전이 시 생성자에게 (수정요청) */
        val revisionRequested: String,
        /** UI_4037 — SIGNED 전이 시 양측에게 (최종 서명 완료) */
        val completed: String,
        /** UI_???? — 신고 접수 시 피신고자에게 (A-8 등록 신청 후 templateId 반영). */
        val disputeReported: String,
        /** UI_???? — 취소 요청 접수 시 피요청자에게 (A'-8 등록 신청 후 templateId 반영). */
        val cancellationRequested: String,
    )
}
