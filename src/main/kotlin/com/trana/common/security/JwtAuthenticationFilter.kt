package com.trana.common.security

import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Authorization: Bearer <token> 헤더를 꺼내 검증하고 SecurityContext에 인증 정보 저장.
 *
 * - 토큰 없음 → 그냥 다음 Filter로 (인증 없는 요청도 통과)
 * - 토큰 있음 + 유효 → SecurityContext에 인증 저장
 * - 토큰 있음 + 무효 → 401 (다음 단계 EntryPoint에서 처리, 지금은 SecurityContext 비움)
 *
 * 인증 강제는 SecurityConfig의 authorizeHttpRequests에서 결정.
 * 이 필터는 그저 "토큰이 있으면 해독"하는 역할.
 */
@Component
class JwtAuthenticationFilter(
    private val jwtProvider: JwtProvider,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = resolveToken(request)
        if (token != null) {
            try {
                val userId = jwtProvider.extractUserId(token)
                val authentication =
                    UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        emptyList(),
                    )
                SecurityContextHolder.getContext().authentication = authentication
            } catch (ex: JwtException) {
                log.debug("JWT verification failed: {}", ex.message)
                SecurityContextHolder.clearContext()
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        return if (header.startsWith(BEARER_PREFIX)) header.substring(BEARER_PREFIX.length) else null
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}
