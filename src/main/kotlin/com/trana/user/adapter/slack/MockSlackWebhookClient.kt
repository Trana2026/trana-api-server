package com.trana.user.adapter.slack

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("test")
class MockSlackWebhookClient : SlackWebhookClient {
    private val log = LoggerFactory.getLogger(MockSlackWebhookClient::class.java)

    override fun sendInquiry(payload: SlackInquiryPayload) {
        log.info(
            "[MOCK Slack] 1:1 문의 — publicCode={}, email={}, title={}, createdAt={}\n{}",
            payload.publicCode,
            payload.email,
            payload.title,
            payload.createdAt,
            payload.content,
        )
    }
}
