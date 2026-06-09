package com.trana.auth

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Apple Sign In Server-to-Server notification request body.
 *
 * 구조: { "payload": "<signed_JWT>" }
 * payload = JWS (ES256), Apple JWKS 로 검증.
 * 검증된 JWT claims 의 events 가 실 이벤트 정보.
 */
@Schema(description = "Apple Sign In Server-to-Server notification (payload = signed JWT)")
data class AppleRevokeNotification(
    @Schema(description = "Apple 가 ES256 서명한 JWT payload (Apple JWKS 로 검증)")
    val payload: String,
)

/**
 * Apple notification 의 events claim 파싱 결과.
 *
 * Apple 이 보내는 events 는 escaped JSON string — Jackson 으로 두 번 파싱:
 * 1차: JWT claims 의 events (string)
 * 2차: 그 string 을 JSON 객체로 (이 DTO)
 */
data class AppleRevokeEvent(
    /** "email-disabled" / "email-enabled" / "consent-revoked" / "account-delete" */
    val type: String,
    /** Apple 영구 user ID — social_accounts.provider_user_id 와 매핑 */
    val sub: String,
    @JsonProperty("event_time") val eventTime: Long? = null,
    val email: String? = null,
    @JsonProperty("is_private_email") val isPrivateEmail: String? = null,
)
