package com.trana.notification.adapter.fcm

/**
 * FCM 푸시 발송 어댑터 (Anti-corruption layer).
 *
 * - 디바이스 토큰 다중 발송 지원 (한 유저의 multi-device 대응 — 명세서 2.4.5)
 * - 본문 / 제목 / deeplink / 커스텀 data 페이로드 전달
 * - 토큰 invalid 시 [FcmSendResult.invalidTokens] 로 반환 → 호출 측 (C-5) 이 DB 정리
 *
 * 활성 분기:
 * - `fcm-live` profile 꺼짐 (기본) → [MockFcmClient] — local/dev/test/prod 모두 기본 Mock
 * - `fcm-live` profile 켜짐 → [LiveFcmClient] (FirebaseApp 필수)
 *
 * Live 발송 활성화: `SPRING_PROFILES_ACTIVE=dev,fcm-live` + `FIREBASE_SERVICE_ACCOUNT_BASE64` env var 채움
 */
interface FcmClient {
    fun send(message: FcmMessage): FcmSendResult
}

/**
 * 푸시 메시지 페이로드.
 *
 * @param tokens 한 유저의 활성 device token 리스트 (multi-device). 빈 리스트 허용 — invalidTokens=[], 0/0 결과로 응답.
 * @param deeplink 알림 탭 시 진입할 앱 내 deeplink (예: "trana://home"). null 이면 OS 가 앱 기본 진입 (홈).
 * @param data FCM data payload — 예: kycSessionId, contractPublicCode 등 알림 표시 / 처리에 필요한 메타
 */
data class FcmMessage(
    val tokens: List<String>,
    val title: String,
    val body: String,
    val deeplink: String? = null,
    val data: Map<String, String> = emptyMap(),
)

/**
 * 멀티캐스트 발송 결과.
 *
 * @param successCount FCM 응답 success 토큰 수
 * @param failureCount FCM 응답 failure 토큰 수
 * @param invalidTokens UNREGISTERED / INVALID_ARGUMENT 응답된 토큰 — DB 정리 대상 (C-5)
 */
data class FcmSendResult(
    val successCount: Int,
    val failureCount: Int,
    val invalidTokens: List<String> = emptyList(),
)
