package com.trana.contract.adapter.kakao

import java.time.Instant

/**
 * 카카오톡 알림톡 발송 어댑터 (Anti-corruption layer).
 *
 * - BSP (NCP/Aligo/Coolsms/Solapi/Bizm) 추상화
 * - W6 진입 시점에는 [MockKakaoAlimtalkClient] 만 wire (local/dev/test/prod 모두)
 * - 카카오 BSP 심사 (1~2주) 완료 후 LiveKakaoAlimtalkClient 추가 + prod profile 교체
 *
 * 템플릿 6종 (모두 사전 등록 + 심사 필수):
 * - [NewContractMessage] : SHARED 전이 시 → 수신자
 * - [ReceiverSignedMessage] : RECEIVER_SIGNED 전이 시 → 생성자
 * - [ContractCompletedMessage] : SIGNED 전이 시 → 양측 각각
 * - [RevisionRequestedMessage] : REVISION_REQUESTED 전이 시 → 생성자
 * - [DisputeReportedMessage] : 신고 접수 시 → 피신고자
 * - [CancellationRequestedMessage] : 취소 요청 접수 시 → 피요청자
 * - [GuardianContractCompletedMessage] : SIGNED 시 미성년 party 의 가입 보호자에게
 */
@Suppress("TooManyFunctions")
interface KakaoAlimtalkClient {
    /** SHARED 전이 시 수신자에게 — `[Trana] 새 계약서 도착` 템플릿 */
    fun sendNewContract(message: NewContractMessage)

    /** RECEIVER_SIGNED 전이 시 생성자에게 — `[Trana] 수신자 서명 완료, 최종 확인 필요` 템플릿 */
    fun sendReceiverSigned(message: ReceiverSignedMessage)

    /** REVISION_REQUESTED 전이 시 생성자에게 — `[Trana] 수정 요청 도착` 템플릿 */
    fun sendRevisionRequested(message: RevisionRequestedMessage)

    /** SIGNED 전이 시 양측 각각에게 — `[Trana] 계약 체결 완료` 템플릿 */
    fun sendCompleted(message: ContractCompletedMessage)

    /** 신고 접수 시 피신고자(수신자)에게 — 신고 접수 안내 템플릿 (UJ_9113) */
    fun sendDisputeReported(message: DisputeReportedMessage)

    /** 신고 접수 시 신고자(접수자)에게 — 신고 접수 완료 템플릿 (UJ_9112) */
    fun sendDisputeFiledReceipt(message: DisputeFiledReceiptMessage)

    /** 취소 요청 접수 시 상대 측에게 — `[Trana] 계약 취소 요청` 템플릿 (UI_????) */
    fun sendCancellationRequested(message: CancellationRequestedMessage)

    /** 취소 확정(CANCELLED) 시 요청자에게 — `[Trana] 계약 취소 완료` 템플릿 (신규, UI_????) */
    fun sendCancellationConfirmed(message: CancellationConfirmedMessage)

    /** 취소 요청 철회 시 상대에게 — 계약 취소 철회 안내 템플릿 (UK_0598) */
    fun sendCancellationRevoked(message: CancellationRevokedMessage)

    /** SIGNED 전이 시 미성년자 가입 보호자에게 — `[Trana] 계약 체결 통보` 템플릿 (심사 대기 UI_????) */
    fun sendGuardianContractCompleted(message: GuardianContractCompletedMessage)

    /** 만료 30분 전 서명 대기 측에게 — 계약 만료 안내 (UJ_9524) */
    fun sendExpiryWarning(message: ExpiryWarningMessage)

    /** 만료(삭제) 시 요청자에게 — 계약 삭제 안내(요청자용) (UJ_9527) */
    fun sendExpiryDeletedToRequester(message: ContractExpiredMessage)

    /** 만료(삭제) 시 미서명자에게 — 계약 삭제 안내(미서명자용) (UJ_9529) */
    fun sendExpiryDeletedToUnsigned(message: ContractExpiredMessage)
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
    val invitationAppUrl: String,
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
    val reviewAppUrl: String,
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
    val downloadAppUrl: String,
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
    val reviewAppUrl: String,
)

/**
 * 신고 접수 시 피신고자에게 — 신고 내용 확인 유도.
 *
 * @param recipientPhone 피신고자 (신고 당한 사용자) phone — E.164 또는 010-XXXX-XXXX
 * @param recipientName 피신고자 이름
 * @param contractTitle 거래 상품명 (치환자 #{상품명})
 * @param reportedAt 신고 접수 시각 (치환자 #{신고일시})
 * @param detailUrl 신고 상세 페이지 deeplink (WL 버튼)
 * 알리고 템플릿 : UI_???? (A-8 신청 후 templateId 반영)
 */
data class DisputeReportedMessage(
    val recipientPhone: String,
    val recipientName: String,
    val contractTitle: String,
    val price: Long,
    val reportedAt: Instant,
    val detailUrl: String,
    val detailAppUrl: String,
)

/**
 * 신고 접수 시 신고자(접수자)에게 — 접수 완료 통보 (UJ_9112).
 * 치환변수: #{접수자명}·#{상품명}·#{거래금액}
 */
data class DisputeFiledReceiptMessage(
    val reporterPhone: String,
    val reporterName: String,
    val contractTitle: String,
    val price: Long,
    val detailUrl: String,
    val detailAppUrl: String,
)

/**
 * 취소 요청 접수 시 피요청자에게 — 취소 내용 확인 유도.
 *
 * @param recipientPhone 피요청자 (취소 요청 받은 측) phone — E.164 또는 010-XXXX-XXXX
 * @param recipientName 피요청자 이름
 * @param contractTitle 거래 상품명 (치환자 #{상품명})
 * @param requestedAt 요청 접수 시각 (치환자 #{요청일시})
 * @param detailUrl 취소 요청 상세 페이지 deeplink (WL 버튼)
 * 알리고 템플릿 : UI_???? (A'-8 신청 후 templateId 반영)
 */
data class CancellationRequestedMessage(
    val recipientPhone: String,
    val recipientName: String,
    val requesterName: String,
    val reason: String,
    val contractTitle: String,
    val price: Long,
    val requestedAt: Instant,
    val detailUrl: String,
    val detailAppUrl: String,
)

/**
 * 취소 확정(CANCELLED) 시 요청자에게 — 계약 취소 완료 통보.
 *
 * 취소 확정된 계약은 상세 접근이 불가(앱에서 제외)하므로 버튼은 **홈**으로 연결.
 * @param homeUrl 웹 홈 폴백(linkMo/linkPc) / @param homeAppUrl 앱 홈 스킴(linkAnd/linkIos)
 */
data class CancellationConfirmedMessage(
    val recipientPhone: String,
    val recipientName: String,
    val contractTitle: String,
    val cancelledAt: Instant,
    val homeUrl: String,
    val homeAppUrl: String,
)

/**
 * 취소 요청 철회 시 상대에게 — 계약이 다시 진행됨 안내 (UK_0598).
 * 치환변수: #{수신자명}·#{상품명}·#{거래금액}. 버튼(계약서 확인하기)은 계약 상세.
 */
data class CancellationRevokedMessage(
    val recipientPhone: String,
    val recipientName: String,
    val contractTitle: String,
    val price: Long,
    val detailUrl: String,
    val detailAppUrl: String,
)

/**
 * SIGNED 전이 시 미성년자 party 의 가입 보호자에게 계약 체결 통보.
 * 정보성 알림 (마케팅 무관). 취소권 존재 안내 + 문의 창구.
 *
 * @param recipientPhone 보호자 phone (identity_verifications.phone WHERE purpose=GUARDIAN, subject=미성년, status=SUCCESS)
 * @param minorName 미성년자 이름 (계약 당사자)
 * @param counterpartyName 성인 상대방 이름
 * @param contractTitle 상품명
 * @param price 거래 금액
 * @param contractDetailUrl 계약 상세 웹 URL
 * 알리고 템플릿 : UI_???? (심사 신청 후 templateId 반영, Task #208)
 */
data class GuardianContractCompletedMessage(
    val recipientPhone: String,
    val guardianName: String,
    val minorName: String,
    val contractTitle: String,
    val price: Long,
    val completedAt: Instant,
)

/**
 * 만료 30분 전 서명 대기 측에게 — 계약 만료 안내 (UJ_9524).
 * 치환변수: #{수신자명}·#{상품명}·#{거래금액}. 버튼(계약서 확인하기)은 홈으로 연결.
 */
data class ExpiryWarningMessage(
    val recipientPhone: String,
    val recipientName: String,
    val contractTitle: String,
    val price: Long,
    val homeUrl: String,
    val homeAppUrl: String,
)

/**
 * 만료(삭제) 통보 — 요청자용(UJ_9527)·미서명자용(UJ_9529) 공용.
 * 치환변수: #{요청자명}/#{수신자명}(=recipientName)·#{상품명}·#{거래금액}. 버튼 없음.
 */
data class ContractExpiredMessage(
    val recipientPhone: String,
    val recipientName: String,
    val contractTitle: String,
    val price: Long,
)
