package com.trana.common.util

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

/**
 * HTTP 요청에서 클라이언트 IP 추출 (통합 유틸).
 *
 * 우선순위:
 * 1. `X-Forwarded-For` 헤더 첫 번째 값 (Railway/CDN 등 reverse proxy 뒤)
 * 2. [HttpServletRequest.remoteAddr] fallback
 *
 * 주의: X-Forwarded-For 는 클라이언트가 위조 가능 — audit 신뢰도 필요 시점엔 Cloudflare / TrustedProxy 등 정책 조합 필요.
 * 현재는 device_token 등록 시 IP → 지역 조회에만 사용 (부정확 허용).
 *
 * 통합 전: [com.trana.common.logging.RequestMdcFilter.resolveClientIp] 가 private 으로 같은 로직 보유 (향후 refactor 후보).
 */
@Component
class IpExtractor {
    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    fun extract(request: HttpServletRequest): String? {
        val xff = request.getHeader(HEADER_XFF)
        val remoteAddr = request.remoteAddr
        log.info("[IpExtractor] X-Forwarded-For='{}' remoteAddr='{}'", xff, remoteAddr)
        if (!xff.isNullOrBlank()) return xff.substringBefore(",").trim()
        return remoteAddr
    }

    companion object {
        private const val HEADER_XFF = "X-Forwarded-For"
    }
}
