package com.trana.contract.adapter.kakao

import java.time.Instant

/**
 * 카카오톡 알림톡 발송 어댑터 (Anti-corruption layer).
 *
 * - BSP (NCP/Aligo/Coolsms/Solapi/Bizm) 추상화
 * - W6 진입 시점에는 [MockKakaoAlimtalkClient] 만 wire (local/dev/test/prod 모두)
 * - 카카오 BSP 심사 (1~2주) 완료 후 LiveKakaoAlimtalkClient 추가 + prod profile 교체
 *
 * 템플릿 4종 (모두 사전 등록 + 심사 필수):
 * - [NewContractMessage] : SHARED 전이 시 → 수신자
 * - [ReceiverSignedMessage] : RECEIVER_SIGNED 전이 시 → 생성자
 * - [ContractCompletedMessage] : SIGNED 전이 시 → 양측 각각
 * - [RevisionRequestedMessage] : REVISION_REQUESTED 전이 시 → 생성자
 */
interface KakaoAlimtalkClient {
    /** SHARED 전이 시 수신자에게 — `[Trana] 새 계약서 도착` 템플릿 */
    fun sendNewContract(message: NewContractMessage)

    /** RECEIVER_SIGNED 전이 시 생성자에게 — `[Trana] 수신자 서명 완료, 최종 확인 필요` 템플릿 */
    fun sendReceiverSigned(message: ReceiverSignedMessage)

    /** REVISION_REQUESTED 전이 시 생성자에게 — `[Trana] 수정 요청 도착` 템플릿 */
    fun sendRevisionRequested(message: RevisionRequestedMessage)

    /** SIGNED 전이 시 양측 각각에게 — `[Trana] 계약 체결 완료` 템플릿 */
    fun sendCompleted(message: ContractCompletedMessage)
}

/**
 * 수신자에게 보내는 첫 알림 — 카톡 invitation URL 클릭 유도.
 *
 * @param receiverPhone E.164 또는 010-XXXX-XXXX
 * @param invitationUrl 수신자가 카톡에서 클릭할 URL (token 포함)
 * 알리고 템플릿 : UI_4032
 */
data class NewContractMessage(
    val receiverPhone: String,
    val receiverName: String,
    val sellerName: String,
    val contractTitle: String,
    val price: Long,
    val invitationUrl: String,
)

/**
 * 수신자 서명 완료 후 생성자에게 — 최종 확인/서명 화면 진입 유도.
 *
 * @param reviewUrl 생성자가 PDF v2 검토 + 최종 서명할 URL (앱 deeplink 또는 web URL)
 * 알리고 템플릿 : UI_4033
 */
data class ReceiverSignedMessage(
    val creatorPhone: String,
    val creatorName: String,
    val receiverName: String,
    val contractTitle: String,
    val price: Long,
    val reviewUrl: String,
)

/**
 * 양측 서명 완료 후 양 당사자에게 — PDF v3 다운로드 안내.
 *
 * @param downloadUrl PDF v3 (양측 서명 박스 채워진 최종본) 다운로드 URL
 */
data class ContractCompletedMessage(
    val recipientPhone: String,
    val recipientName: String,
    val contractTitle: String,
    val price: Long,
    val completedAt: Instant,
    val downloadUrl: String,
)

/**
 * 수신자 수정 요청 발생 시 생성자에게 — 수정 모드 진입 유도.
 *
 * @param reviewUrl 생성자가 수정 요청 화면 진입할 URL
 * @param revisionReason 필드별 사유를 "라벨: 사유" 한 줄씩 \n join — 예: "제목: 오타 수정 필요\n가격: 100만원으로 조정"
 * 알리고 템플릿 : UI_4034
 */
data class RevisionRequestedMessage(
    val creatorPhone: String,
    val creatorName: String,
    val contractTitle: String,
    val requesterName: String,
    val price: Long,
    val revisionReason: String,
    val reviewUrl: String,
)
