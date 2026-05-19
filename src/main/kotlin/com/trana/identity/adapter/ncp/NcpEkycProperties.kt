package com.trana.identity.adapter.ncp

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "trana.kyc.ncp")
data class NcpEkycProperties(
    val idCard: Endpoint,
    val faceCompare: Endpoint,
) {
    data class Endpoint(
        val invokeUrl: String,
        val secretKey: String,
    )
}
