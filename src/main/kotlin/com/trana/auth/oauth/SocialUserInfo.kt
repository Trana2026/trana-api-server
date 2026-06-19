package com.trana.auth.oauth

/**
 * 소셜 공급자에서 가져온 사용자 정보 (provider 통합 모델).
 *
 * 모든 OAuth 공급자가 같은 필드를 주는 건 아니므로 nullable.
 * - Apple: 첫 로그인에만 email 제공, 이후 null
 * - Kakao: name(nickname claim)/email은 사용자 동의 항목 (거부 가능)
 */
data class SocialUserInfo(
    val provider: SocialProvider,
    val providerUserId: String,
    val email: String? = null,
    val name: String? = null,
)
