package com.trana.user.adapter.slack

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "trana.slack")
data class SlackProperties(
    @field:NotBlank
    val webhookUrl: String,
)
