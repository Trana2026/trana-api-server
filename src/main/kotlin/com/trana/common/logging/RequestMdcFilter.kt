package com.trana.common.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * 요청 컨텍스트를 MDC 에 채워 모든 로그에 requestId/path/method 자동 표시 (refactor qq).
 *
 * - requestId: `X-Request-Id` 헤더 우선, 없으면 UUID 생성 (short 8자)
 * - path: requestURI
 * - method: HTTP method
 * - 응답 헤더에도 `X-Request-Id` 회신 → 클라이언트 ↔ 서버 로그 연결
 * - 요청 종료 시 MDC.clear — thread pool 재사용 시 누설 차단
 *
 * 우선순위 HIGHEST_PRECEDENCE — 모든 다른 filter (JWT 등) 보다 먼저 컨텍스트 채움.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestMdcFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId =
            request.getHeader(HEADER_REQUEST_ID)?.takeIf { it.isNotBlank() }
                ?: UUID.randomUUID().toString().take(8)
        try {
            MDC.put(MDC_REQUEST_ID, requestId)
            MDC.put(MDC_PATH, request.requestURI)
            MDC.put(MDC_METHOD, request.method)
            resolveClientIp(request)?.let { MDC.put(MDC_IP, it) }
            request.getHeader("User-Agent")?.let { MDC.put(MDC_USER_AGENT, it) }
            response.setHeader(HEADER_REQUEST_ID, requestId)
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_REQUEST_ID)
            MDC.remove(MDC_PATH)
            MDC.remove(MDC_METHOD)
            MDC.remove(MDC_IP)
            MDC.remove(MDC_USER_AGENT)
        }
    }

    /** X-Forwarded-For 우선 (Railway proxy 환경), 없으면 remoteAddr. */
    private fun resolveClientIp(request: HttpServletRequest): String? {
        val xff = request.getHeader("X-Forwarded-For")
        if (!xff.isNullOrBlank()) return xff.substringBefore(",").trim()
        return request.remoteAddr
    }

    companion object {
        private const val HEADER_REQUEST_ID = "X-Request-Id"
        private const val MDC_REQUEST_ID = "requestId"
        private const val MDC_PATH = "path"
        private const val MDC_METHOD = "method"
        private const val MDC_IP = "ip"
        private const val MDC_USER_AGENT = "userAgent"
    }
}
