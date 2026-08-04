package com.trana.analytics

import java.util.UUID

/**
 * 서버 권위(authoritative) 분석 이벤트.
 *
 * 규칙(데이터 추적 플랜):
 * - *_completed 등 권위 이벤트는 서버 성공 시점에만 전송(클라이언트 성공 화면만으로 금지).
 * - **PII 금지**: 이름·전화·계약 원문·서명·이미지·검색어·문의/신고 본문을 properties 에 절대 넣지 않는다.
 *   contract_id 는 익명 식별자(publicCode)만 사용.
 * - [eventId] 로 클라이언트·서버 중복 제거(Amplitude insert_id / GA4 event param).
 *
 * @param name        내부 이벤트명(= Amplitude event_type). 예) contract_signed
 * @param gaEventName GA4 매핑 이벤트명(권장 이벤트). 예) sign_up/login/share. null 이면 [name] 사용.
 * @param userId      로그인 사용자 식별자. 비로그인(익명) 이벤트는 null.
 * @param properties  PII 없는 이벤트 속성.
 */
data class AnalyticsEvent(
    val name: String,
    val userId: Long?,
    val properties: Map<String, Any?> = emptyMap(),
    val gaEventName: String? = null,
    val eventId: String = UUID.randomUUID().toString(),
)

/** 서버 권위 이벤트명 상수 (데이터 추적 플랜 01_Events, Source=Server). */
object AnalyticsEvents {
    const val VERIFICATION_COMPLETED = "verification_completed" // EVT-004
    const val ACCOUNT_CREATED = "account_created" // EVT-007 (GA4 sign_up)
    const val LOGIN_COMPLETED = "login_completed" // EVT-008 (GA4 login)
    const val AI_ANALYSIS_COMPLETED = "ai_analysis_completed" // EVT-021
    const val CONTRACT_DRAFT_GENERATED = "contract_draft_generated" // EVT-026
    const val CONTRACT_DRAFT_DELETED = "contract_draft_deleted" // EVT-030
    const val CONTRACT_SHARE_COMPLETED = "contract_share_completed" // EVT-033 (GA4 share)
    const val GUARDIAN_VERIFICATION_COMPLETED = "guardian_verification_completed" // EVT-043
    const val CONTRACT_CHANGE_REQUESTED = "contract_change_requested" // EVT-045
    const val CONTRACT_SIGNED = "contract_signed" // EVT-047
    const val CONTRACT_REVISION_SUBMITTED = "contract_revision_submitted" // EVT-050
    const val CONTRACT_REVISION_SHARED = "contract_revision_shared" // EVT-051
    const val FINAL_SIGNATURE_REQUESTED = "final_signature_requested" // EVT-052
    const val TRANSACTION_COMPLETED = "transaction_completed" // EVT-056
    const val CONTRACT_CANCEL_REQUESTED = "contract_cancel_requested" // EVT-057
    const val CONTRACT_CANCEL_RESPONDED = "contract_cancel_responded" // EVT-058
    const val ISSUE_REPORT_SUBMITTED = "issue_report_submitted" // EVT-060
    const val SUPPORT_INQUIRY_SUBMITTED = "support_inquiry_submitted" // EVT-061
}
