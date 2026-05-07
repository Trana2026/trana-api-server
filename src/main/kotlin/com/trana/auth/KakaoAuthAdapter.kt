package com.trana.auth

import com.fasterxml.jackson.annotation.JsonProperty
import com.trana.user.SocialProvider
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * Kakao OAuth 어댑터.
 *
 * 클라이언트(Flutter)가 받아온 Kakao access_token으로 사용자 정보 조회.
 * Kakao Developers 앱 등록 불필요 (토큰 검증만, OAuth 흐름은 클라이언트가 관리).
 */
@Component
class KakaoAuthAdapter : SocialAuthAdapter {
    override val provider = SocialProvider.KAKAO

    private val restClient = RestClient.create(KAKAO_API_BASE_URL)

    override fun fetchUserInfo(accessToken: String): SocialUserInfo {
        val response = try {
            restClient.get()
                .uri("/v2/user/me")
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .body(KakaoUserResponse::class.java)
        } catch (ex: RestClientException) {
            throw AuthException.InvalidSocialToken(SocialProvider.KAKAO, cause = ex)
        } ?: throw AuthException.InvalidSocialToken(SocialProvider.KAKAO)

        return SocialUserInfo(
            provider = SocialProvider.KAKAO,
            providerUserId = response.id.toString(),
            email = response.kakaoAccount?.email,
            nickname = response.kakaoAccount?.profile?.nickname,
        )
    }

    companion object {
        private const val KAKAO_API_BASE_URL = "https://kapi.kakao.com"
    }
}

private data class KakaoUserResponse(
    val id: Long,
    @JsonProperty("kakao_account")
    val kakaoAccount: KakaoAccount? = null,
) {
    data class KakaoAccount(val email: String? = null, val profile: KakaoProfile? = null) {
        data class KakaoProfile(val nickname: String? = null)
    }
}
