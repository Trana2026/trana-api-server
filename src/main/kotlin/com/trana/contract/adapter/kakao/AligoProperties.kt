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
        /** UJ_9113 — 신고 접수 시 피신고자(수신자)에게. */
        val disputeReported: String,
        /** UJ_9112 — 신고 접수 시 신고자(접수자)에게. */
        val disputeFiledReceipt: String = "UI_PENDING_DISPUTE_FILED_RECEIPT",
        /** UJ_9111 — 취소 요청 접수 시 피요청자에게. */
        val cancellationRequested: String,
        /** UI_???? — 취소 확정(CANCELLED) 시 요청자에게. 신규 등록 대기 → placeholder default. */
        val cancellationConfirmed: String = "UI_PENDING_CANCELLATION_CONFIRMED",
        /** UK_0598 — 취소 요청 철회 시 상대에게. */
        val cancellationRevoked: String = "UI_PENDING_CANCELLATION_REVOKED",
        /** UI_4032 강조 타이틀 (강조 표기형 템플릿 필수). */
        val emtitleNewContract: String,
        /** UI_4033 강조 타이틀. */
        val emtitleReceiverSigned: String,
        /** UI_4034 강조 타이틀. */
        val emtitleRevisionRequested: String,
        /** UI_4037 강조 타이틀. */
        val emtitleCompleted: String,
        /** UI_???? — SIGNED 시 미성년자 party 의 가입 보호자에게 (계약 체결 통보, Task #208 등록 후 templateId 반영). */
        val guardianContractCompleted: String,
        /** UJ_9524 — 만료 30분 전 서명 대기 측에게. */
        val expiryWarning: String = "UI_PENDING_EXPIRY_WARNING",
        /** UJ_9527 — 만료(삭제) 시 요청자에게. */
        val expiryDeletedRequester: String = "UI_PENDING_EXPIRY_DELETED_REQUESTER",
        /** UJ_9529 — 만료(삭제) 시 미서명자에게. */
        val expiryDeletedUnsigned: String = "UI_PENDING_EXPIRY_DELETED_UNSIGNED",
        /** 강조 타이틀 (심사 시 강조 표기형 결정, 우선 필드만 추가). */
        val emtitleGuardianContractCompleted: String,
        /** UJ_9113 강조 타이틀. */
        val emtitleDisputeReported: String = "신고 접수 안내",
        /** UJ_9112 강조 타이틀. */
        val emtitleDisputeFiledReceipt: String = "신고 접수 완료",
        /** UJ_9111 강조 타이틀. */
        val emtitleCancellationRequested: String = "계약 취소 요청",
        /** UK_0598 강조 타이틀. */
        val emtitleCancellationRevoked: String = "계약 취소 철회 안내",
        /** UJ_9524 강조 타이틀. */
        val emtitleExpiryWarning: String = "계약 만료 안내",
        /** UJ_9527/UJ_9529 강조 타이틀(공용). */
        val emtitleExpiryDeleted: String = "계약 삭제 안내",
    )
}
