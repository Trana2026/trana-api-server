package com.trana.common.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.time.Instant

/**
 * 글로벌 예외 핸들러.
 *
 * 처리 우선순위:
 * 1. DomainException (우리 비즈니스 예외) → @ExceptionHandler가 잡음
 * 2. Spring의 표준 예외 (Validation, JSON 파싱 등) → 부모 클래스가 자동 ProblemDetail 변환
 *    - 단, Validation은 errors 필드 추가 위해 override
 * 3. 그 외 모든 Exception → 캐치올 500
 *
 * 로깅 정책:
 * - 4xx: WARN (스택트레이스 X — 사용자 잘못)
 * - 5xx: ERROR (스택트레이스 O — 우리 잘못)
 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /** 우리 비즈니스 예외 처리. */
    @ExceptionHandler(DomainException::class)
    fun handleDomain(ex: DomainException): ResponseEntity<ProblemDetail> {
        val errorCode = ex.errorCode
        val problemDetail =
            ProblemDetail
                .forStatusAndDetail(
                    errorCode.status,
                    ex.message ?: errorCode.message,
                ).apply {
                    title = errorCode.code
                    setProperty("code", errorCode.code)
                    setProperty("timestamp", Instant.now().toString())
                }

        if (errorCode.status.is5xxServerError) {
            log.error("DomainException [{}]: {}", errorCode.code, ex.message, ex)
        } else {
            log.warn("DomainException [{}]: {}", errorCode.code, ex.message)
        }

        return ResponseEntity.status(errorCode.status).body(problemDetail)
    }

    /**
     * @Valid 검증 실패. Spring이 자동 ProblemDetail 만들어주지만,
     * 우리 표준 (code, errors 배열, timestamp) 추가 위해 override.
     */
    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val errors =
            ex.bindingResult.fieldErrors.map {
                mapOf(
                    "field" to it.field,
                    "message" to (it.defaultMessage ?: "유효하지 않은 값"),
                    "rejectedValue" to it.rejectedValue?.toString(),
                )
            }
        val errorCode = ErrorCode.INVALID_INPUT
        val problemDetail =
            ProblemDetail
                .forStatusAndDetail(
                    errorCode.status,
                    "입력값 검증에 실패했습니다",
                ).apply {
                    title = errorCode.code
                    setProperty("code", errorCode.code)
                    setProperty("timestamp", Instant.now().toString())
                    setProperty("errors", errors)
                }

        log.warn("Validation failed: {} field(s)", errors.size)

        return ResponseEntity.status(errorCode.status).body(problemDetail)
    }

    /** 처리 안 된 모든 Exception → 500. */
    @ExceptionHandler(Exception::class)
    fun handleUnknown(ex: Exception): ResponseEntity<ProblemDetail> {
        val errorCode = ErrorCode.INTERNAL_SERVER_ERROR
        val problemDetail =
            ProblemDetail
                .forStatusAndDetail(
                    errorCode.status,
                    errorCode.message,
                ).apply {
                    title = errorCode.code
                    setProperty("code", errorCode.code)
                    setProperty("timestamp", Instant.now().toString())
                }

        log.error("Unhandled exception", ex)

        return ResponseEntity.status(errorCode.status).body(problemDetail)
    }
}
