package com.trana.auth.oauth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "trana.oauth.google")
data class GoogleOidcProperties(
    val issuer: String,
    val clientId: String,
)
