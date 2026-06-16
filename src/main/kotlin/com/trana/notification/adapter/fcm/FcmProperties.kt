package com.trana.notification.adapter.fcm

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * FCM 설정.
 *
 * service-account-base64: Firebase Console > 프로젝트 설정 > 서비스 계정 >
 * "새 비공개 키 생성" 으로 받은 JSON 을 base64 인코딩한 한 줄 문자열.
 *
 * 빈 값 → FirebaseConfig 가 빈 등록 skip → MockFcmClient 가 사용됨 (Step C-2).
 */
@ConfigurationProperties(prefix = "trana.fcm")
data class FcmProperties(
    val serviceAccountBase64: String = "",
)
