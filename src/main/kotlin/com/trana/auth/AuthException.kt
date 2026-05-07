package com.trana.auth

import com.trana.common.exception.DomainException
import com.trana.common.exception.ErrorCode
import com.trana.user.SocialProvider

sealed class AuthException(errorCode: ErrorCode, message: String? = null, cause: Throwable? = null) :
    DomainException(errorCode, message, cause) {
    class InvalidSocialToken(provider: SocialProvider, cause: Throwable? = null) :
        AuthException(
            errorCode = ErrorCode.INVALID_SOCIAL_TOKEN,
            message = "${provider.name} 토큰 검증 실패",
            cause = cause,
        )

    class UnsupportedProvider(provider: SocialProvider) :
        AuthException(
            errorCode = ErrorCode.UNSUPPORTED_PROVIDER,
            message = "지원하지 않는 공급자: ${provider.name}",
        )

    class InvalidToken(detail: String) :
        AuthException(
            errorCode = ErrorCode.INVALID_TOKEN,
            message = "토큰 검증 실패: $detail",
        )
}
