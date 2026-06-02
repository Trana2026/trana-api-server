package com.trana.common.security

import com.trana.common.exception.ErrorCode
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.time.Instant

/**
 * Authorization: Bearer <token> 헤더를 꺼내 검증하고 SecurityContext에 인증 정보 저장.
 *
 * - 토큰 없음 → 그냥 다음 Filter로 (인증 없는 요청도 통과)
 * - 토큰 있음 + 유효 → SecurityContext에 인증 저장
 * - 토큰 있음 + 무효 → **401 즉시 응답 (chain 진행 안 함)** (refactor w)
 *   permitAll endpoint 에서 만료/위변조 토큰이 익명 요청과 구분 없이 통과되던 결함 차단
 *
 * 인증 강제는 SecurityConfig의 authorizeHttpRequests에서 결정.
 */
@Component
class JwtAuthenticationFilter(
    private val jwtProvider: JwtProvider,
    private val objectMapper: ObjectMapper,
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
                val userId = jwtProvider.extractUserIdFromAccessToken(token)
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
                writeInvalidTokenResponse(request, response)
                return
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun writeInvalidTokenResponse(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val errorCode = ErrorCode.INVALID_TOKEN
        val problemDetail =
            ProblemDetail.forStatusAndDetail(errorCode.status, errorCode.message).apply {
                title = errorCode.code
                instance = URI.create(request.requestURI)
                setProperty("code", errorCode.code)
                setProperty("timestamp", Instant.now().toString())
            }
        response.status = errorCode.status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = "UTF-8"
        objectMapper.writeValue(response.writer, problemDetail)
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        return if (header.startsWith(BEARER_PREFIX)) header.substring(BEARER_PREFIX.length) else null
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}
