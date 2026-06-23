package com.trana.user.adapter.slack

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
@Profile("local", "dev", "prod")
class LiveSlackWebhookClient(
    properties: SlackProperties,
) : SlackWebhookClient {
    private val log = LoggerFactory.getLogger(LiveSlackWebhookClient::class.java)
    private val restClient = RestClient.create(properties.webhookUrl)
    private val createdAtFormatter: DateTimeFormatter =
        DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Seoul"))

    override fun sendInquiry(payload: SlackInquiryPayload) {
        val body =
            mapOf(
                "text" to "[Trana] 새 1:1 문의 — ${payload.title}",
                "blocks" to
                    listOf(
                        mapOf(
                            "type" to "header",
                            "text" to
                                mapOf(
                                    "type" to "plain_text",
                                    "text" to "새 1:1 문의 도착",
                                ),
                        ),
                        mapOf(
                            "type" to "section",
                            "fields" to
                                listOf(
                                    mapOf("type" to "mrkdwn", "text" to "*문의 ID*\n`${payload.publicCode}`"),
                                    mapOf("type" to "mrkdwn", "text" to "*회신 이메일*\n${payload.email}"),
                                    mapOf("type" to "mrkdwn", "text" to "*제목*\n${payload.title}"),
                                    mapOf(
                                        "type" to "mrkdwn",
                                        "text" to "*작성 시각*\n${createdAtFormatter.format(payload.createdAt)}",
                                    ),
                                ),
                        ),
                        mapOf("type" to "divider"),
                        mapOf(
                            "type" to "section",
                            "text" to
                                mapOf(
                                    "type" to "mrkdwn",
                                    "text" to "*내용*\n```\n${payload.content}\n```",
                                ),
                        ),
                    ),
            )
        runCatching {
            restClient
                .post()
                .body(body)
                .retrieve()
                .toBodilessEntity()
        }.onFailure { ex ->
            log.warn("Slack webhook 발송 실패 — publicCode=${payload.publicCode}: ${ex.message}", ex)
            throw ex
        }
    }
}
