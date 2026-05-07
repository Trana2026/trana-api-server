package com.trana.auth.oauth

import com.trana.user.SocialProvider

/**
 * 소셜 공급자별 OAuth 연동 추상화.
 *
 * 클라이언트가 받아온 access_token을 받아 공급자 API 호출 → 사용자 정보 반환.
 *
 * 각 공급자별 구현:
 * - KakaoAuthAdapter: Kakao API
 * - GoogleAuthAdapter: Google OAuth (W2 이후)
 * - AppleAuthAdapter: Apple Sign In (W2 이후)
 */
interface SocialAuthAdapter {
    /** 어떤 provider를 처리하는지 식별. */
    val provider: SocialProvider

    /**
     * 공급자 access_token을 검증하고 사용자 정보 조회.
     *
     * @throws RuntimeException 토큰 무효 / API 호출 실패
     */
    fun fetchUserInfo(accessToken: String): SocialUserInfo
}
