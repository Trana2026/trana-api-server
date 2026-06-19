package com.trana.user.adapter.slack

import java.time.Instant

interface SlackWebhookClient {
    /** 1:1 문의 작성 시 Slack 채널로 발송. 실패해도 사용자 응답은 200 — 운영 로그만 남기고 swallow (Service 책임). */
    fun sendInquiry(payload: SlackInquiryPayload)
}

data class SlackInquiryPayload(
    val publicCode: String,
    val email: String,
    val title: String,
    val content: String,
    val createdAt: Instant,
)
