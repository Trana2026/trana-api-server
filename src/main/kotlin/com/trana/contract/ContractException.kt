package com.trana.contract

import com.trana.common.exception.DomainException
import com.trana.common.exception.ErrorCode

/**
 * 계약 도메인 예외.
 *
 * - NotFound: publicCode 또는 id 로 찾지 못함
 * - NotOwner: creator 가 아닌 user 의 수정/삭제 시도
 * - NotDraft: DRAFT 가 아닌 상태에서 수정/삭제 시도
 * - AlreadyDeleted: soft delete 된 계약 접근
 * - MaxAttachments: 사진 7장 초과 등록 시도
 * - AttachmentNotFound: 첨부 id 매칭 실패
 * - InvalidConsentType: 계약 보호자 동의 처리 중 부적합 (성인 요청 / 잘못된 토큰 purpose 등)
 * - GuardianConsentRequired: 계약 보호자 동의 승인 시 미성년의 가입 단계 보호자 verification 매핑 실패
 * - GuardianConsentAlready: 이미 보호자 동의 완료 상태에서 재요청
 * - AiExtractionFailed: OpenAI 호출 실패 (5xx / timeout)
 * - AiResponseInvalid: OpenAI 응답 JSON 파싱 / schema 검증 실패
 * - PdfNotGenerated: PDF 다운로드 요청 시 markReady 가 선행되지 않음
 * - UserNotReady: invitation accept 시 user 가입 미완료 (status != ACTIVE 또는 미성년 보호자 미검증)
 * - RoleAlreadySet: creatorRole 이미 설정된 계약에 다시 PATCH 시도
 * - NotReceiver: 수신자 전용 endpoint 에 생성자 본인 또는 외부 user 호출
 * - TermsMismatch: 수신자 서명 시 agreedTermIds 가 contract domain term ID 와 불일치
 * - NotInReceiverSignedState: 생성자 최종 서명 시점 status 가 RECEIVER_SIGNED 가 아님
 */
sealed class ContractException(
    errorCode: ErrorCode,
    message: String? = null,
    cause: Throwable? = null,
) : DomainException(errorCode, message, cause) {
    class NotFound(
        publicCode: String,
    ) : ContractException(
            ErrorCode.CONTRACT_NOT_FOUND,
            "계약을 찾을 수 없습니다 (publicCode=$publicCode)",
        )

    class NotOwner(
        publicCode: String,
        userId: Long,
    ) : ContractException(
            ErrorCode.CONTRACT_NOT_OWNER,
            "본인이 작성한 계약만 수정할 수 있습니다 (publicCode=$publicCode, userId=$userId)",
        )

    class NotDraft(
        publicCode: String,
        currentStatus: String,
    ) : ContractException(
            ErrorCode.CONTRACT_NOT_DRAFT,
            "DRAFT 상태에서만 수정/삭제할 수 있습니다 (publicCode=$publicCode, status=$currentStatus)",
        )

    class AlreadyDeleted(
        publicCode: String,
    ) : ContractException(
            ErrorCode.CONTRACT_ALREADY_DELETED,
            "이미 삭제된 계약입니다 (publicCode=$publicCode)",
        )

    class MaxAttachments(
        publicCode: String,
        current: Int,
    ) : ContractException(
            ErrorCode.CONTRACT_MAX_ATTACHMENTS,
            "사진은 최대 7장까지 업로드할 수 있습니다 (publicCode=$publicCode, current=$current)",
        )

    class AttachmentNotFound(
        attachmentId: Long,
    ) : ContractException(
            ErrorCode.CONTRACT_ATTACHMENT_NOT_FOUND,
            "첨부 파일을 찾을 수 없습니다 (attachmentId=$attachmentId)",
        )

    class InvalidConsentType(
        reason: String,
    ) : ContractException(
            ErrorCode.CONTRACT_INVALID_CONSENT_TYPE,
            "계약 보호자 동의 처리 중 부적합한 요청입니다: $reason",
        )

    class GuardianConsentRequired(
        publicCode: String,
    ) : ContractException(
            ErrorCode.CONTRACT_GUARDIAN_CONSENT_REQUIRED,
            "보호자 동의가 완료되지 않은 계약입니다 (publicCode=$publicCode)",
        )

    class GuardianConsentAlready(
        publicCode: String,
    ) : ContractException(
            ErrorCode.CONTRACT_GUARDIAN_CONSENT_ALREADY,
            "이미 보호자 동의가 완료된 계약입니다 (publicCode=$publicCode)",
        )

    class AiExtractionFailed(
        message: String,
        cause: Throwable? = null,
    ) : ContractException(ErrorCode.CONTRACT_AI_EXTRACTION_FAILED, message, cause)

    class AiResponseInvalid(
        reason: String,
        cause: Throwable? = null,
    ) : ContractException(
            ErrorCode.CONTRACT_AI_RESPONSE_INVALID,
            "AI 응답을 파싱할 수 없습니다: $reason",
            cause,
        )

    class NotReadyEligible(
        publicCode: String,
        missingFields: String,
    ) : ContractException(
            ErrorCode.CONTRACT_NOT_READY_ELIGIBLE,
            "READY 전이 불가 — 누락 필드: $missingFields (publicCode=$publicCode)",
        )

    class NotInReadyState(
        publicCode: String,
        currentStatus: String,
    ) : ContractException(
            ErrorCode.CONTRACT_NOT_IN_READY_STATE,
            "현재 READY 상태가 아닙니다 (publicCode=$publicCode, status=$currentStatus)",
        )

    class PdfNotGenerated(
        publicCode: String,
        currentStatus: String,
    ) : ContractException(
            ErrorCode.CONTRACT_PDF_NOT_GENERATED,
            "PDF 가 아직 생성되지 않았습니다 (publicCode=$publicCode, status=$currentStatus, markReady 가 선행 필요)",
        )

    class AiImageCountInvalid(
        requested: Int,
    ) : ContractException(
            ErrorCode.CONTRACT_AI_IMAGE_COUNT_INVALID,
            "AI 분석 입력 사진 개수 위반 (requested=$requested, allowed=1~2)",
        )

    class AiExtractionNotFound(
        extractionId: Long,
    ) : ContractException(
            ErrorCode.CONTRACT_AI_EXTRACTION_NOT_FOUND,
            "AI 추출 결과를 찾을 수 없습니다 (extractionId=$extractionId)",
        )

    class RevisionRequestNotFound(
        publicCode: String,
    ) : ContractException(
            ErrorCode.CONTRACT_REVISION_REQUEST_NOT_FOUND,
            "수정 요청 이력이 없습니다 (publicCode=$publicCode)",
        )

    class InvitationNotFound(
        token: String,
    ) : ContractException(
            ErrorCode.CONTRACT_INVITATION_NOT_FOUND,
            "초대 토큰을 찾을 수 없습니다 (token=$token)",
        )

    class InvitationExpired(
        token: String,
    ) : ContractException(
            ErrorCode.CONTRACT_INVITATION_EXPIRED,
            "이미 사용되었거나 만료된 초대 토큰입니다 (token=$token)",
        )

    class NotInSharedState(
        publicCode: String,
        currentStatus: String,
    ) : ContractException(
            ErrorCode.CONTRACT_NOT_IN_SHARED_STATE,
            "현재 SHARED 상태가 아닙니다 (publicCode=$publicCode, status=$currentStatus)",
        )

    class NotInRevisionRequestedState(
        publicCode: String,
        currentStatus: String,
    ) : ContractException(
            ErrorCode.CONTRACT_NOT_IN_REVISION_REQUESTED_STATE,
            "현재 REVISION_REQUESTED 상태가 아닙니다 (publicCode=$publicCode, status=$currentStatus)",
        )

    class UserNotReady(
        userId: Long,
        reason: String,
    ) : ContractException(
            ErrorCode.CONTRACT_USER_NOT_READY,
            "가입이 완료되지 않은 사용자입니다 (userId=$userId, $reason)",
        )

    class NotAccessible(
        publicCode: String,
        userId: Long,
    ) : ContractException(
            ErrorCode.CONTRACT_NOT_ACCESSIBLE,
            "이 계약에 접근할 권한이 없습니다 (publicCode=$publicCode, userId=$userId)",
        )

    class GuardianNotVerified(
        userId: Long,
    ) : ContractException(
            ErrorCode.CONTRACT_GUARDIAN_NOT_VERIFIED,
            "가입 보호자 인증이 완료되지 않은 미성년자입니다 (userId=$userId)",
        )

    class RoleAlreadySet(
        publicCode: String,
    ) : ContractException(
            ErrorCode.CONTRACT_ROLE_ALREADY_SET,
            "이미 역할이 설정된 계약입니다 (publicCode=$publicCode)",
        )

    class NotReceiver(
        publicCode: String,
        userId: Long,
    ) : ContractException(
            ErrorCode.CONTRACT_NOT_RECEIVER,
            "수신자만 서명할 수 있습니다 (publicCode=$publicCode, userId=$userId)",
        )

    class NotReceiverSeller(
        publicCode: String,
        userId: Long,
    ) : ContractException(
            ErrorCode.CONTRACT_NOT_RECEIVER_SELLER,
            "수신자(SELLER)만 보증기간을 변경할 수 있습니다 (publicCode=$publicCode, userId=$userId)",
        )

    class TermsMismatch(
        expected: String,
        actual: List<Long>,
    ) : ContractException(
            ErrorCode.CONTRACT_TERMS_MISMATCH,
            "동의해야 할 약관 ID 가 일치하지 않습니다 (expected=$expected, actual=$actual)",
        )

    class NotInReceiverSignedState(
        publicCode: String,
        currentStatus: String,
    ) : ContractException(
            ErrorCode.CONTRACT_NOT_IN_RECEIVER_SIGNED_STATE,
            "현재 RECEIVER_SIGNED 상태가 아닙니다 (publicCode=$publicCode, status=$currentStatus)",
        )

    class NotInSignedState(
        publicCode: String,
        currentStatus: String,
    ) : ContractException(
            ErrorCode.CONTRACT_NOT_IN_SIGNED_STATE,
            "현재 SIGNED 상태가 아닙니다 (publicCode=$publicCode, status=$currentStatus)",
        )

    class AlreadyCompletedByParty(
        publicCode: String,
        userId: Long,
    ) : ContractException(
            ErrorCode.CONTRACT_ALREADY_COMPLETED_BY_PARTY,
            "이미 거래 완료를 클릭한 사용자입니다 (publicCode=$publicCode, userId=$userId)",
        )
}
