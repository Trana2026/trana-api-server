package com.trana.user

import com.trana.common.exception.DomainException
import com.trana.common.exception.ErrorCode

/**
 * User 도메인 예외 계층.
 *
 * sealed class로 같은 패키지 내 sub-class만 허용 → 컴파일 타임 안전.
 * 새 예외 종류 추가 시 이 파일에 sub-class 추가.
 */
sealed class UserException(errorCode: ErrorCode, message: String? = null, cause: Throwable? = null) :
    DomainException(errorCode, message, cause) {

    class NotFound(identifier: String) :
        UserException(
            errorCode = ErrorCode.USER_NOT_FOUND,
            message = "사용자를 찾을 수 없습니다 (id=$identifier)",
        )
}
