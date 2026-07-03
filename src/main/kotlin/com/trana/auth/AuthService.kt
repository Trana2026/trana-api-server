package com.trana.auth

import com.trana.audit.AuditEvent
import com.trana.audit.AuditLogger
import com.trana.common.security.JwtProvider
import com.trana.notification.service.DeviceTokenService
import com.trana.user.service.UserService
import org.springframework.stereotype.Service

/**
 * JWT refresh + logout 처리.
 *
 * - refresh: 만료 임박 access 재발급 (refresh token 검증)
 * - logout: audit + optional device token 정리 (JWT 자체는 stateless, access 15분 자연 만료)
 */
@Service
class AuthService(
    private val userService: UserService,
    private val jwtProvider: JwtProvider,
    private val auditLogger: AuditLogger,
    private val deviceTokenService: DeviceTokenService,
) {
    fun refresh(request: RefreshRequest): SignInResponse {
        val userId =
            try {
                jwtProvider.extractUserIdFromRefreshToken(request.refreshToken)
            } catch (ex: io.jsonwebtoken.JwtException) {
                throw AuthException.InvalidToken("refresh token 검증 실패: ${ex.message}", cause = ex)
            }

        val user = userService.getById(userId)

        return SignInResponse(
            accessToken = jwtProvider.createAccessToken(userId),
            refreshToken = jwtProvider.createRefreshToken(userId),
            publicCode = user.publicCode,
        )
    }

    fun logout(
        userId: Long,
        deviceToken: String?,
    ) {
        auditLogger.log(
            eventType = AuditEvent.USER_SIGNED_OUT,
            actorUserId = userId,
            entityType = "USER",
            entityId = userId,
        )
        if (deviceToken != null) {
            deviceTokenService.unregister(userId, deviceToken)
        }
    }
}
