package com.trana.user.service
import com.trana.analytics.AnalyticsEvent
import com.trana.analytics.AnalyticsEvents
import com.trana.analytics.AnalyticsTracker
import com.trana.common.util.TokenGenerator
import com.trana.user.UserException
import com.trana.user.adapter.slack.SlackInquiryPayload
import com.trana.user.adapter.slack.SlackWebhookClient
import com.trana.user.entity.UserInquiry
import com.trana.user.repository.UserInquiryRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 1:1 문의 단방향 흐름.
 *
 * - create: DB INSERT + Slack webhook 발송 (실패해도 사용자 200 — 운영 로그만)
 * - findMine: 본인 row 목록 (최신순)
 * - findByPublicCode: 본인 row 상세 — 다른 user 의 publicCode 추측 시 404 (정보 누출 방어)
 */
@Service
@Transactional
class UserInquiryService(
    private val userInquiryRepository: UserInquiryRepository,
    private val slackWebhookClient: SlackWebhookClient,
    private val tokenGenerator: TokenGenerator,
    private val analyticsTracker: AnalyticsTracker,
) {
    private val log = LoggerFactory.getLogger(UserInquiryService::class.java)

    fun create(
        userId: Long,
        email: String,
        title: String,
        content: String,
    ): UserInquiry {
        val inquiry =
            userInquiryRepository.save(
                UserInquiry(
                    publicCode = tokenGenerator.generatePublicCode(),
                    userId = userId,
                    email = email,
                    title = title,
                    content = content,
                ),
            )
        runCatching {
            slackWebhookClient.sendInquiry(
                SlackInquiryPayload(
                    publicCode = inquiry.publicCode,
                    email = inquiry.email,
                    title = inquiry.title,
                    content = inquiry.content,
                    createdAt = inquiry.createdAt!!,
                ),
            )
        }.onFailure { ex ->
            log.warn("Slack 알림 실패 — publicCode=${inquiry.publicCode}: ${ex.message}", ex)
        }
        // EVT-061 support_inquiry_submitted — 문의 접수 성공(서버). 이메일/제목/본문 원문 금지.
        // inquiry_category 는 현재 엔티티에 카테고리 필드가 없어 미포함(필드 추가 시 반영).
        analyticsTracker.track(
            AnalyticsEvent(
                name = AnalyticsEvents.SUPPORT_INQUIRY_SUBMITTED,
                userId = userId,
            ),
        )
        return inquiry
    }

    @Transactional(readOnly = true)
    fun findMine(userId: Long): List<UserInquiry> = userInquiryRepository.findAllByUserIdOrderByCreatedAtDesc(userId)

    @Transactional(readOnly = true)
    fun findByPublicCode(
        publicCode: String,
        userId: Long,
    ): UserInquiry =
        userInquiryRepository.findByPublicCodeAndUserId(publicCode, userId)
            ?: throw UserException.InquiryNotFound(publicCode)
}
