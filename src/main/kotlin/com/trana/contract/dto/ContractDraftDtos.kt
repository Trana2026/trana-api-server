package com.trana.contract.dto

import com.trana.contract.entity.ConsentType
import com.trana.contract.entity.ContractStatus
import com.trana.contract.entity.DeliveryType
import com.trana.contract.entity.DisputeState
import com.trana.contract.entity.PartyType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.Instant

@Schema(description = "계약 DRAFT 생성 요청")
data class CreateContractDraftRequest(
    @field:NotNull
    @field:Schema(description = "거래 방식 (대면 / 택배)", example = "DIRECT")
    val deliveryType: DeliveryType,
    @field:NotNull
    @field:Schema(description = "작성자 본인의 역할 (SELLER / BUYER)", example = "SELLER")
    val creatorRole: PartyType,
)

@Schema(description = "계약 DRAFT 부분 수정 요청 — null 인 필드는 변경 없음")
data class UpdateContractDraftRequest(
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
    @field:Size(max = LOCATION_MAX_LENGTH)
    @field:Schema(description = "거래 희망 지역", example = "서울 강남구", maxLength = LOCATION_MAX_LENGTH)
    val location: String? = null,
    @field:Schema(description = "거래 방식 변경", example = "SHIPPING")
    val deliveryType: DeliveryType? = null,
)

@Schema(description = "계약 단건 응답")
data class ContractResponse(
    @field:Schema(description = "외부 노출 식별자 (jnanoid 12자)", example = "Yx7Kp2qLm9Nz")
    val publicCode: String,
    @field:Schema(description = "계약 상태")
    val status: ContractStatus,
    @field:Schema(description = "분쟁 상태 (W7+)")
    val disputeState: DisputeState,
    @field:Schema(description = "거래 방식")
    val deliveryType: DeliveryType,
    @field:Schema(description = "보호자 동의 유형")
    val consentType: ConsentType,
    val title: String?,
    val price: Long?,
    val conditionSummary: String?,
    val conditionDetails: String?,
    @field:Schema(description = "보증 기간 (일)", example = "3")
    val warrantyPeriodDays: Int,
    val location: String?,
    @field:Schema(description = "보호자 동의 완료 시각 (미성년 계약 + 동의 완료 시에만 채워짐)")
    val guardianConsentAt: Instant?,
    @field:Schema(description = "리비전 버전 (W5+)", example = "1")
    val version: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Schema(description = "본인 계약 목록 아이템")
data class ContractListItem(
    val publicCode: String,
    val status: ContractStatus,
    val title: String?,
    val price: Long?,
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

private const val TITLE_MAX_LENGTH = 200
private const val LOCATION_MAX_LENGTH = 100
