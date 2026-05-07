package com.trana.auth.oauth

import com.fasterxml.jackson.annotation.JsonProperty
import com.trana.auth.AuthException
import com.trana.user.SocialProvider
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
class GoogleAuthAdapter : SocialAuthAdapter {
    override val provider = SocialProvider.GOOGLE

    private val restClient = RestClient.create(GOOGLE_API_BASE_URL)

    override fun fetchUserInfo(accessToken: String): SocialUserInfo {
        val response = try {
            restClient.get()
                .uri("/oauth2/v3/userinfo")
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .body(GoogleUserResponse::class.java)
        } catch (ex: RestClientException) {
            throw AuthException.InvalidSocialToken(SocialProvider.GOOGLE, cause = ex)
        } ?: throw AuthException.InvalidSocialToken(SocialProvider.GOOGLE)

        return SocialUserInfo(
            provider = SocialProvider.GOOGLE,
            providerUserId = response.sub,
            email = response.email,
            nickname = response.name,
        )
    }

    companion object {
        private const val GOOGLE_API_BASE_URL = "https://www.googleapis.com"
    }
}

private data class GoogleUserResponse(
    val sub: String,
    val email: String? = null,
    val name: String? = null,
    @JsonProperty("email_verified")
    val emailVerified: Boolean? = null,
)
