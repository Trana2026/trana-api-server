package com.trana.contract.dto

import com.trana.contract.entity.ConsentType
import com.trana.contract.entity.ContractStatus
import com.trana.contract.entity.DeliveryType
import com.trana.contract.entity.DisputeState
import com.trana.contract.entity.PartyType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.Instant

@Schema(
    description = """
계약 DRAFT 생성 요청 — 세 필드 모두 nullable (PATCH 로 채울 수 있음).

흐름별 권장:
- 성인: 한 번에 deliveryType + creatorRole 같이 전송 (consentType 생략 가능 → NOT_APPLICABLE 자동)
- 미성년 (a 동의 요청): consentType=GUARDIAN_REQUIRED 만 먼저, role/deliveryType 은 보호자 동의 후 PATCH
- 미성년 (b 동의 없이 진행): consentType=NOT_APPLICABLE 만 먼저, role/deliveryType 은 이어서 PATCH

검증:
- 성인이 consentType=GUARDIAN_REQUIRED 보내면 400
- 미성년이 guardianVerifiedAt=null 이면 403 (가입 보호자 eKYC 미완료)
  """,
)
data class CreateContractDraftRequest(
    @field:Schema(description = "거래 방식 (대면 / 택배) — 생략 가능, 이후 PATCH 로 채움", example = "DIRECT")
    val deliveryType: DeliveryType? = null,
    @field:Schema(description = "작성자 본인의 역할 (SELLER / BUYER) — 생략 가능, 이후 PATCH 로 채움", example = "SELLER")
    val creatorRole: PartyType? = null,
    @field:Schema(
        description = "보호자 동의 유형. 성인은 생략 또는 NOT_APPLICABLE. 미성년은 GUARDIAN_REQUIRED / NOT_APPLICABLE 명시.",
        example = "GUARDIAN_REQUIRED",
    )
    val consentType: ConsentType? = null,
)

@Schema(description = "계약 DRAFT 부분 수정 요청 — null 인 필드는 변경 없음")
data class UpdateContractDraftRequest(
    @field:Size(max = TRADING_PLATFORM_MAX_LENGTH)
    @field:Schema(
        description = "거래 발견 플랫폼 (자유 텍스트, 50자 이내)",
        example = "당근마켓",
        maxLength = TRADING_PLATFORM_MAX_LENGTH,
    )
    val tradingPlatform: String? = null,
    @field:Size(max = TITLE_MAX_LENGTH)
    @field:Schema(description = "상품명", example = "아이폰 15 Pro 256GB 블랙", maxLength = TITLE_MAX_LENGTH)
    val title: String? = null,
    @field:PositiveOrZero
    @field:Schema(description = "거래 가격 (원, 정수)", example = "1200000")
    val price: Long? = null,
    @field:Schema(description = "상태 요약 한 줄", example = "미개봉 새상품")
    val conditionSummary: String? = null,
    @field:Schema(description = "상태/하자/포함품 상세", example = "박스 미개봉, 정품 보증서 포함, 액정 보호필름 별도")
    val conditionDetails: String? = null,
    @field:Schema(description = "거래 방식 (IN_PROGRESS 단계에서 미정 가능 — null). markReady 시점에 NOT NULL 강제.")
    val deliveryType: DeliveryType?,
    @field:Schema(
        description = """
작성자 본인의 역할 (SELLER / BUYER) — 한 번만 설정 가능, 이미 설정된 후 변경 시 409.
미성년 (a) 흐름에서 보호자 동의 완료 후 "역할 선택" 단계에서 채움. 성인은 createDraft 시점에 채워졌으면 null.
  """,
        example = "SELLER",
    )
    val creatorRole: PartyType? = null,
)

@Schema(description = "계약 단건 응답")
data class ContractResponse(
    @field:Schema(description = "외부 노출 식별자 (jnanoid 12자)", example = "Yx7Kp2qLm9Nz")
    val publicCode: String,
    @field:Schema(description = "계약 상태")
    val status: ContractStatus,
    @field:Schema(description = "분쟁 상태 (W7+)")
    val disputeState: DisputeState,
    @field:Schema(description = "거래 방식 (IN_PROGRESS 단계에서 미정 가능 — null). markReady 시점에 NOT NULL 강제.")
    val deliveryType: DeliveryType?,
    @field:Schema(description = "보호자 동의 유형")
    val consentType: ConsentType,
    @field:Schema(description = "거래 발견 플랫폼", example = "당근마켓")
    val tradingPlatform: String?,
    val title: String?,
    val price: Long?,
    val conditionSummary: String?,
    val conditionDetails: String?,
    @field:Schema(description = "보증 기간 (일)", example = "3")
    val warrantyPeriodDays: Int,
    @field:Schema(description = "보호자 동의 완료 시각 (미성년 계약 + 동의 완료 시에만 채워짐)")
    val guardianConsentAt: Instant?,
    @field:Schema(description = "리비전 버전 (W5+)", example = "1")
    val version: Int,
    @field:Schema(
        description = "PDF SHA-256 (READY 이상에서 채워짐, DRAFT 시 null)",
        example = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    )
    val contentHash: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Schema(description = "본인 계약 목록 아이템 — 본인이 만든 것 + 받은 것 모두 포함")
data class ContractListItem(
    val publicCode: String,
    val status: ContractStatus,
    val title: String?,
    val price: Long?,
    @field:Schema(
        description =
            "본인의 역할 — SELLER 또는 BUYER. 프론트가 (myRole, status) 조합으로 " +
                "알림 메시지 매핑. **null 인 경우** = 본인이 creator 지만 아직 role 미설정 " +
                "(IN_PROGRESS 단계의 임시저장 — 미성년 (a) 흐름에서 보호자 동의 후 PATCH 로 채움)",
        example = "BUYER",
    )
    val myRole: PartyType?,
    @field:Schema(description = "첨부 사진 개수 (0~7)", example = "3")
    val attachmentCount: Int,
    @field:Schema(
        description = "첫 번째 사진 (sortOrder=0) presigned GET URL — 리스트 thumbnail 용. 없으면 null. TTL 5분",
        example = "https://trana-contract-dev.s3.../attachments/.../0.jpg?...",
    )
    val firstAttachmentUrl: String?,
    val updatedAt: Instant,
)

@Schema(description = "계약 상태 전이 로그 (WORM audit)")
data class ContractStatusLogResponse(
    @field:Schema(description = "log row id", example = "1")
    val id: Long,
    @field:Schema(description = "이전 상태 (null = INITIAL = 계약 생성 시점)")
    val fromStatus: ContractStatus?,
    @field:Schema(description = "전이 후 상태")
    val toStatus: ContractStatus,
    @field:Schema(description = "전이를 일으킨 user (null = 시스템 자동)", example = "42")
    val actorUserId: Long?,
    @field:Schema(description = "사유 (취소 사유 등 — 현재 null)")
    val reason: String?,
    @field:Schema(description = "전이 시각 (UTC)")
    val changedAt: Instant,
)

@Schema(description = "PDF 다운로드 응답 (presigned GET URL)")
data class ContractPdfDownloadResponse(
    @field:Schema(description = "presigned GET URL (지정 TTL 내 다운로드)")
    val downloadUrl: String,
    @field:Schema(description = "URL 유효 시간 (초)", example = "600")
    val expiresInSeconds: Long,
    @field:Schema(description = "PDF SHA-256 (다운로드 후 검증용)")
    val sha256: String,
)

@Schema(description = "계약 공유 요청 — markReady(READY) → SHARED 전이 + 카카오톡 알림톡 발송")
data class ShareContractRequest(
    @field:NotBlank
    @field:Size(max = RECEIVER_NAME_MAX_LENGTH)
    @field:Schema(
        description = "수신자 이름 (audit 기록)",
        example = "홍길동",
        maxLength = RECEIVER_NAME_MAX_LENGTH,
    )
    val receiverName: String,
    @field:NotBlank
    @field:Pattern(regexp = RECEIVER_PHONE_PATTERN)
    @field:Schema(
        description = "수신자 전화번호 — 한국 핸드폰 또는 E.164 (알림톡 발송 대상)",
        example = "010-1234-5678",
        pattern = RECEIVER_PHONE_PATTERN,
    )
    val receiverPhone: String,
)

@Schema(description = "수신자 수정 요청 — 필드별 reason (최소 1개 필수)")
data class RequestRevisionRequest(
    @field:Size(max = REVISION_REASON_MAX_LENGTH)
    @field:Schema(
        description = "거래 물품명 수정 이유",
        example = "상품명을 더 정확히 작성해주세요",
        maxLength = REVISION_REASON_MAX_LENGTH,
    )
    val titleReason: String? = null,
    @field:Size(max = REVISION_REASON_MAX_LENGTH)
    @field:Schema(
        description = "거래 금액 수정 이유",
        example = "150,000원으로 조정 부탁드립니다",
        maxLength = REVISION_REASON_MAX_LENGTH,
    )
    val priceReason: String? = null,
    @field:Size(max = REVISION_REASON_MAX_LENGTH)
    @field:Schema(
        description = "상품 상태 수정 이유",
        maxLength = REVISION_REASON_MAX_LENGTH,
    )
    val conditionSummaryReason: String? = null,
    @field:Size(max = REVISION_REASON_MAX_LENGTH)
    @field:Schema(
        description = "상품 상세 설명 수정 이유",
        maxLength = REVISION_REASON_MAX_LENGTH,
    )
    val conditionDetailsReason: String? = null,
) {
    @AssertTrue(message = "최소 1개 필드의 reason 은 채워야 합니다")
    @Schema(hidden = true)
    fun isAtLeastOneReasonPresent(): Boolean =
        !titleReason.isNullOrBlank() ||
            !priceReason.isNullOrBlank() ||
            !conditionSummaryReason.isNullOrBlank() ||
            !conditionDetailsReason.isNullOrBlank()
}

@Schema(description = "수신자 서명 요청 — 계약 약관 + 전자서명 약관 동의 + PNG 서명 image base64")
data class ReceiverSignRequest(
    @field:NotBlank
    @field:Size(max = SIGNATURE_BASE64_MAX_LENGTH)
    @field:Schema(
        description =
            "전자서명 PNG image base64 인코딩 (data URI prefix 없이 raw base64). " +
                "frontend signature 패키지의 png bytes → base64 변환",
        example = "iVBORw0KGgoAAAANSUhEUgAA...",
        maxLength = SIGNATURE_BASE64_MAX_LENGTH,
    )
    val signatureBase64: String,
    @field:NotEmpty
    @field:Size(min = REQUIRED_TERM_COUNT, max = MAX_TERM_COUNT)
    @field:Schema(
        description =
            "동의한 약관 ID 목록 — CONTRACT_AGREEMENT + ELECTRONIC_SIGNATURE 2개 필수. " +
                "frontend 가 GET /v1/terms?context=CONTRACT 로 조회한 ID 그대로 전달",
        example = "[5, 6]",
    )
    val agreedTermIds: List<Long>,
)

@Schema(description = "수신자 서명 완료 응답 — RECEIVER_SIGNED 전이 + PDF v2 갱신")
data class ReceiverSignResponse(
    @field:Schema(description = "외부 노출 식별자", example = "Yx7Kp2qLm9Nz")
    val publicCode: String,
    @field:Schema(description = "전이 후 상태", example = "RECEIVER_SIGNED")
    val status: ContractStatus,
    @field:Schema(description = "서명된 PDF 리비전 version (contract_signatures.pdf_version_at_sign 과 일치)", example = "1")
    val pdfVersion: Int,
    @field:Schema(description = "수신자 서명 시각 (UTC)")
    val receiverSignedAt: Instant,
)

@Schema(description = "생성자 최종 서명 요청 — 계약 약관 + 전자서명 약관 동의 + PNG 서명 image base64")
data class CreatorSignRequest(
    @field:NotBlank
    @field:Size(max = SIGNATURE_BASE64_MAX_LENGTH)
    @field:Schema(
        description = "전자서명 PNG image base64 인코딩 (data URI prefix 없이 raw base64)",
        example = "iVBORw0KGgoAAAANSUhEUgAA...",
        maxLength = SIGNATURE_BASE64_MAX_LENGTH,
    )
    val signatureBase64: String,
    @field:NotEmpty
    @field:Size(min = REQUIRED_TERM_COUNT, max = MAX_TERM_COUNT)
    @field:Schema(
        description = "동의한 약관 ID 목록 — CONTRACT_AGREEMENT + ELECTRONIC_SIGNATURE 2개 필수",
        example = "[5, 6]",
    )
    val agreedTermIds: List<Long>,
)

@Schema(description = "생성자 최종 서명 완료 응답 — SIGNED 전이 + PDF v3 갱신")
data class CreatorSignResponse(
    @field:Schema(description = "외부 노출 식별자", example = "Yx7Kp2qLm9Nz")
    val publicCode: String,
    @field:Schema(description = "전이 후 상태", example = "SIGNED")
    val status: ContractStatus,
    @field:Schema(description = "서명된 PDF 리비전 version", example = "1")
    val pdfVersion: Int,
    @field:Schema(description = "생성자 서명 시각 (UTC)")
    val creatorSignedAt: Instant,
)

@Schema(
    description = """
  거래 완료 확인 응답.
  - 한쪽만 클릭 시: status=SIGNED 유지, 본인 partyCompletedAt 만 채움
  - 양측 모두 클릭 완료 시: status=COMPLETED 자동 전이 + completedAt 채움 (보증기간 3일 기준)
  """,
)
data class ConfirmCompletionResponse(
    @field:Schema(description = "외부 노출 식별자", example = "Yx7Kp2qLm9Nz")
    val publicCode: String,
    @field:Schema(description = "전이 후 상태 (양측 미완료면 SIGNED, 양측 완료면 COMPLETED)", example = "SIGNED")
    val status: ContractStatus,
    @field:Schema(description = "판매자(SELLER)가 거래 완료 클릭한 시각 (UTC), 미클릭 시 null")
    val sellerCompletedAt: Instant?,
    @field:Schema(description = "구매자(BUYER)가 거래 완료 클릭한 시각 (UTC), 미클릭 시 null")
    val buyerCompletedAt: Instant?,
    @field:Schema(description = "양측 모두 완료된 시각 (UTC), 보증기간 시작 기준점. 한쪽만 완료면 null")
    val completedAt: Instant?,
)

private const val TITLE_MAX_LENGTH = 200
private const val RECEIVER_NAME_MAX_LENGTH = 50
private const val RECEIVER_PHONE_PATTERN = "^[0-9+\\-]{10,20}$"
private const val REVISION_REASON_MAX_LENGTH = 500
private const val SIGNATURE_BASE64_MAX_LENGTH = 262144
private const val REQUIRED_TERM_COUNT = 2
private const val MAX_TERM_COUNT = 10
private const val TRADING_PLATFORM_MAX_LENGTH = 50
