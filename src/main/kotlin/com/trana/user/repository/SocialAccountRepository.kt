package com.trana.user.repository

import com.trana.auth.oauth.SocialProvider
import com.trana.user.entity.SocialAccount
import org.springframework.data.jpa.repository.JpaRepository

interface SocialAccountRepository : JpaRepository<SocialAccount, Long> {
    fun findByProviderAndProviderUserId(
        provider: SocialProvider,
        providerUserId: String,
    ): SocialAccount?
}
