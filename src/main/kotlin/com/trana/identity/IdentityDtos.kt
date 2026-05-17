package com.trana.identity

import com.trana.identity.adapter.FaceCompareResult
import com.trana.identity.adapter.Gender
import com.trana.identity.adapter.IdCardOcrOutput
import com.trana.identity.adapter.IdCardRecognitionResult
import com.trana.identity.adapter.IdCardSensitiveData
import com.trana.identity.adapter.IdCardVerifyResult
import com.trana.identity.adapter.IdType
import com.trana.identity.adapter.idType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

@Schema(description = "신분증 OCR 응답 (4종 신분증 flat 구조)")
@Suppress("LongParameterList")
data class IdCardOcrResponse(
    @Schema(description = "Verify 호출 키 (10분 유효)", example = "f75ec54f-e6fd-...")
    val requestId: String,
    @Schema(description = "신분증 종류", example = "ID_CARD")
    val idType: IdType,
    @Schema(description = "이름", example = "홍길동")
    val name: String,
    @Schema(description = "생년월일", example = "1985-01-01")
    val birthDate: LocalDate,
    @Schema(description = "성별", example = "MALE")
    val gender: Gender,
    @Schema(description = "마스킹된 식별번호 (화면 표시용)", example = "850101-1******")
    val maskedIdNumber: String?,
    @Schema(description = "발급일", nullable = true)
    val issueDate: LocalDate?,
    @Schema(description = "NCP isConfident (참고용, 행안부 진위확인 아님)")
    val rawConfidence: Boolean,
    @Schema(description = "Face Compare에 쓸 얼굴 이미지 포함 여부")
    val hasFaceImage: Boolean,
    @Schema(description = "주민번호 SHA-256 해시 (ic/dl)", nullable = true)
    val personalNumberHash: String? = null,
    @Schema(description = "주소 (ic/dl)", nullable = true)
    val address: String? = null,
    @Schema(description = "면허번호 (dl)", nullable = true)
    val licenseNumber: String? = null,
    @Schema(description = "여권번호 (pp)", nullable = true)
    val passportNumber: String? = null,
    @Schema(description = "여권 만료일 (pp)", nullable = true)
    val expireDate: LocalDate? = null,
    @Schema(description = "외국인등록번호 SHA-256 해시 (ac)", nullable = true)
    val alienRegNumberHash: String? = null,
    @Schema(description = "국적 ISO 3166 (pp/ac)", nullable = true)
    val nationality: String? = null,
    @Schema(description = "비자 종류 (ac)", nullable = true)
    val visaType: String? = null,
)

@Schema(description = "얼굴 비교 응답")
data class FaceCompareResponse(
    @Schema(description = "유사도 (0.0 ~ 1.0)", example = "0.92")
    val similarity: Double,
    @Schema(description = "도메인 threshold 통과 여부", example = "true")
    val isMatch: Boolean,
)

fun IdCardOcrOutput.toResponse(): IdCardOcrResponse =
    result
        .toBaseResponse(sensitive.requestId)
        .copy(maskedIdNumber = maskIdNumber(sensitive))

private fun IdCardRecognitionResult.toBaseResponse(requestId: String): IdCardOcrResponse {
    val base =
        IdCardOcrResponse(
            requestId = requestId,
            idType = idType,
            name = name,
            birthDate = birthDate,
            gender = gender,
            maskedIdNumber = null,
            issueDate = issueDate,
            rawConfidence = rawConfidence,
            hasFaceImage = faceImageBase64 != null,
        )
    return when (this) {
        is IdCardRecognitionResult.ResidentIdCard -> {
            base.copy(personalNumberHash = personalNumberHash, address = address)
        }

        is IdCardRecognitionResult.DriverLicense -> {
            base.copy(
                personalNumberHash = personalNumberHash,
                address = address,
                licenseNumber = licenseNumber,
            )
        }

        is IdCardRecognitionResult.Passport -> {
            base.copy(
                passportNumber = passportNumber,
                expireDate = expireDate,
                nationality = nationality,
            )
        }

        is IdCardRecognitionResult.AlienRegistration -> {
            base.copy(
                alienRegNumberHash = alienRegNumberHash,
                nationality = nationality,
                visaType = visaType,
            )
        }
    }
}

private fun maskIdNumber(s: IdCardSensitiveData): String? =
    when {
        s.personalNumber != null -> maskRrn(s.personalNumber)
        s.passportNumber != null -> maskPassport(s.passportNumber)
        else -> null
    }

private fun maskRrn(rrn: String): String =
    if (rrn.length < 7) {
        "*".repeat(rrn.length)
    } else {
        "${rrn.take(6)}-${rrn[6]}${"*".repeat(6)}"
    }

private fun maskPassport(num: String): String =
    if (num.length <= 2) {
        "*".repeat(num.length)
    } else {
        "${num.take(2)}${"*".repeat(num.length - 2)}"
    }

fun FaceCompareResult.toResponse(): FaceCompareResponse =
    FaceCompareResponse(similarity = similarity, isMatch = isMatch)

@Schema(description = "신분증 진위확인 요청")
data class IdCardVerifyRequest(
    @Schema(
        description = "OCR 단계에서 받은 requestId (10분 유효)",
        requiredMode = Schema.RequiredMode.REQUIRED,
        example = "f75ec54f-e6fd-...",
    )
    @NotBlank(message = "requestId는 필수입니다")
    val requestId: String,
)

@Schema(description = "신분증 진위확인 응답")
data class IdCardVerifyResponse(
    @Schema(description = "진위확인 통과 여부 (행안부/경찰청)", example = "true")
    val isValid: Boolean,
    @Schema(description = "에러 코드 (실패 시 NCP가 반환)", nullable = true)
    val errorCode: String? = null,
    @Schema(description = "에러 메시지 (실패 시)", nullable = true)
    val errorMessage: String? = null,
)

fun IdCardVerifyResult.toResponse(): IdCardVerifyResponse =
    IdCardVerifyResponse(
        isValid = isValid,
        errorCode = errorCode,
        errorMessage = errorMessage,
    )
