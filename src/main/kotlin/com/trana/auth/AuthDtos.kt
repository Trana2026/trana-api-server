package com.trana.auth

import com.trana.user.SocialProvider

/**
 * 소셜 로그인 요청.
 *
 * - provider: KAKAO / GOOGLE / APPLE
 * - accessToken: 클라이언트(Flutter)가 SDK로 받아온 공급자 access_token
 */
data class SocialSignInRequest(val provider: SocialProvider, val accessToken: String)

/**
 * 로그인 응답.
 *
 * - accessToken: 우리 서버 발급 JWT (15분)
 * - refreshToken: 우리 서버 발급 JWT (30일)
 * - publicCode: 외부 노출용 사용자 식별자 (nanoid 12자)
 */
data class SignInResponse(val accessToken: String, val refreshToken: String, val publicCode: String)
