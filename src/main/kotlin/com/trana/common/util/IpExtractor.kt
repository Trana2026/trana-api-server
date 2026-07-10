package com.trana.common.util

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

/**
 * HTTP 요청에서 클라이언트 IP 추출 (통합 유틸).
 *
 * 우선순위:
 * 1. `CF-Connecting-IP` — Cloudflare orange proxy 뒤일 때 (dev-api.trana.kr / prod). 표준 XFF 대신 CF 가 자체 헤더로 실 client IP 전달
 * 2. `X-Forwarded-For` 첫 번째 값 — 일반 reverse proxy 뒤 (CF 없는 환경)
 * 3. [HttpServletRequest.remoteAddr] fallback (proxy 없는 로컬 환경)
 *
 * 주의: 두 헤더 모두 클라이언트가 위조 가능 — audit 신뢰도 필요 시점엔 Cloudflare / TrustedProxy 등 정책 조합 필요.
 * 현재는 device_token 등록 시 IP → 지역 조회에만 사용 (부정확 허용).
 *
 * 통합 전: [com.trana.common.logging.RequestMdcFilter.resolveClientIp] 가 private 으로 같은 로직 보유 (향후 refactor 후보 — CF 우선순위 반영 필요).
 */
@Component
class IpExtractor {
    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    fun extract(request: HttpServletRequest): String? {
        val cfIp = request.getHeader(HEADER_CF_CONNECTING_IP)
        if (!cfIp.isNullOrBlank()) return cfIp.trim()
        val xff = request.getHeader(HEADER_XFF)
        if (!xff.isNullOrBlank()) return xff.substringBefore(",").trim()
        return request.remoteAddr
    }

    companion object {
        private const val HEADER_CF_CONNECTING_IP = "CF-Connecting-IP"
        private const val HEADER_XFF = "X-Forwarded-For"
    }
}
