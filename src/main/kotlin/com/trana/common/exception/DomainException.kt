package com.trana.common.exception

/**
 * 비즈니스 예외 베이스 클래스.
 *
 * - abstract → 직접 인스턴스화 금지, 도메인별 하위 클래스로만 사용
 * - 각 도메인 패키지에서 상속 (예: com.trana.user.UserException.NotFound)
 * - errorCode가 HTTP status + 클라이언트 식별자 보유
 * - cause로 원인 예외 체이닝 (외부 API 실패 등 디버깅용)
 *
 * 사용 예 (W2부터):
 * class UserException {
 *     class NotFound(userId: Long) : DomainException(
 *         ErrorCode.USER_NOT_FOUND,
 *         "사용자를 찾을 수 없습니다 (id=$userId)"
 *     )
 * }
 */
abstract class DomainException(val errorCode: ErrorCode, message: String? = null, cause: Throwable? = null) :
    RuntimeException(message ?: errorCode.message, cause)
