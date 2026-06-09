package com.trana.auth

import com.trana.audit.AuditEvent
import com.trana.audit.AuditLogger
import com.trana.auth.oauth.SocialProvider
import com.trana.user.UserException
import com.trana.user.repository.SocialAccountRepository
import com.trana.user.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/**
 * Apple Sign In Server-to-Server notification 처리.
 *
 * 흐름:
 * 1. payload (signed JWT) → Apple JWKS 로 검증 (appleIdTokenDecoder 재사용 — iss + multi-aud Services ID)
 * 2. events claim (escaped JSON string) 파싱 → AppleRevokeEvent
 * 3. type 별 분기:
 *    - account-delete / consent-revoked → UserService.withdraw (멱등: AlreadyWithdrawn 무시)
 *    - email-disabled / email-enabled → audit 기록만
 * 4. 모든 경우 controller 에서 200 OK (Apple 재전송 방지)
 *
 * 트랜잭션: NOT_SUPPORTED — JWKS fetch 외부 I/O 가 트랜잭션 안 들어가지 않도록.
 * userService.withdraw 의 자체 @Transactional 만 짧게.
 */
@Service
class AppleRevokeService(
    @Qualifier("appleIdTokenDecoder")
    private val jwtDecoder: JwtDecoder,
    private val objectMapper: ObjectMapper,
    private val socialAccountRepository: SocialAccountRepository,
    private val userService: UserService,
    private val auditLogger: AuditLogger,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun process(notification: AppleRevokeNotification) {
        val event =
            try {
                decodeAndExtract(notification.payload)
            } catch (ex: JwtException) {
                log.warn("Apple revoke JWT verify failed: {}", ex.message)
                return
            } catch (ex: IllegalArgumentException) {
                log.warn("Apple revoke parse failed: {}", ex.message)
                return
            }

        when (event.type) {
            EVENT_ACCOUNT_DELETE, EVENT_CONSENT_REVOKED -> handleWithdraw(event)
            EVENT_EMAIL_DISABLED, EVENT_EMAIL_ENABLED -> handleEmailToggle(event)
            else -> log.warn("Apple revoke unknown type={}, sub={}", event.type, event.sub)
        }
    }

    private fun decodeAndExtract(payload: String): AppleRevokeEvent {
        val jwt = jwtDecoder.decode(payload)
        val eventsJson =
            jwt.getClaimAsString("events")
                ?: throw IllegalArgumentException("events claim missing")
        return objectMapper.readValue(eventsJson, AppleRevokeEvent::class.java)
    }

    private fun handleWithdraw(event: AppleRevokeEvent) {
        val socialAccount =
            socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.APPLE, event.sub)
        if (socialAccount == null) {
            log.info("Apple revoke {}: unknown sub={} (never signed up)", event.type, event.sub)
            return
        }
        try {
            userService.withdraw(socialAccount.userId)
        } catch (ex: UserException.AlreadyWithdrawn) {
            log.info(
                "Apple revoke {}: already withdrawn (idempotent), sub={}, reason={}",
                event.type,
                event.sub,
                ex.message,
            )
        }
        auditLogger.log(
            eventType = AuditEvent.APPLE_REVOKE_NOTIFICATION,
            actorUserId = socialAccount.userId,
            entityType = ENTITY_USER,
            entityId = socialAccount.userId,
            metadata = mapOf("type" to event.type, "sub" to event.sub),
        )
    }

    private fun handleEmailToggle(event: AppleRevokeEvent) {
        val socialAccount =
            socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.APPLE, event.sub)
        auditLogger.log(
            eventType = AuditEvent.APPLE_REVOKE_NOTIFICATION,
            actorUserId = socialAccount?.userId,
            entityType = ENTITY_USER,
            entityId = socialAccount?.userId,
            metadata =
                mapOf(
                    "type" to event.type,
                    "sub" to event.sub,
                    "email" to (event.email ?: ""),
                    "isPrivateEmail" to (event.isPrivateEmail ?: ""),
                ),
        )
    }

    companion object {
        private const val EVENT_ACCOUNT_DELETE = "account-delete"
        private const val EVENT_CONSENT_REVOKED = "consent-revoked"
        private const val EVENT_EMAIL_DISABLED = "email-disabled"
        private const val EVENT_EMAIL_ENABLED = "email-enabled"
        private const val ENTITY_USER = "USER"
    }
}
