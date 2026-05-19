package com.trana.identity.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

@Schema(description = "OCR 결과 응답 (사용자 본인 확인 후 Verify 단계 진입)")
data class RecognizeIdCardResponse(
    @Schema(description = "NCP Document API requestId — 이후 step에 그대로 전달", example = "20a4...")
    val requestId: String,
    @Schema(description = "신분증 종류", example = "ID_CARD")
    val idType: String,
    @Schema(description = "이름 (OCR 결과)", example = "홍길동")
    val name: String,
    @Schema(description = "생년월일", example = "1990-01-01")
    val birthDate: LocalDate,
    @Schema(description = "성별", example = "MALE")
    val gender: String,
)

@Schema(description = "신분증 진위확인 요청")
data class VerifyIdCardRequest(
    @field:NotBlank
    @Schema(description = "OCR 단계에서 받은 requestId", example = "20a4...")
    val requestId: String,
)

@Schema(description = "신분증 진위확인 응답")
data class VerifyIdCardResponse(
    @Schema(example = "20a4...")
    val requestId: String,
    @Schema(example = "true")
    val verified: Boolean,
)

@Schema(description = "휴대폰 번호 기록 요청 (Verify 통과 후만 호출 가능)")
data class RecordPhoneRequest(
    @field:NotBlank
    @Schema(description = "OCR 단계에서 받은 requestId", example = "20a4...")
    val requestId: String,
    @field:NotBlank
    @Schema(description = "휴대폰 번호 (010 + 8자리, 하이픈 무관)", example = "01012345678")
    val phone: String,
)

@Schema(description = "휴대폰 번호 기록 응답")
data class RecordPhoneResponse(
    @Schema(example = "20a4...")
    val requestId: String,
    @Schema(description = "정규화된 휴대폰 번호 (하이픈 제거 11자리)", example = "01012345678")
    val phone: String,
)

@Schema(
    description = """
  성인 가입 완료 응답.
  얼굴 비교 SUCCESS → user 생성 + JWT 발급. 클라는 토큰 저장 후 인증 흐름으로 진입.
  """,
)
data class SignUpResponse(
    @Schema(description = "JWT access token (15분)")
    val accessToken: String,
    @Schema(description = "JWT refresh token (30일)")
    val refreshToken: String,
    @Schema(description = "외부 노출용 사용자 코드 (jnanoid 12자)", example = "Vh7sK2x9Pq3R")
    val publicCode: String,
    @Schema(
        description = "성인 가입은 항상 false. Phase 6 미성년자 흐름에서 true 가능 — 보호자 인증 안내 필요",
        example = "false",
    )
    val requiresGuardian: Boolean,
)

@Schema(description = "보호자 신분증 진위확인 요청")
data class GuardianVerifyIdCardRequest(
    @field:NotBlank
    @Schema(description = "OCR 단계에서 받은 requestId", example = "20a4...")
    val requestId: String,
    @field:NotBlank
    @Schema(description = "guardian_links 토큰 (미성년자에게 받은 URL의 token 부분)", example = "V1StGXR8_Z5jdHi6B-myT")
    val token: String,
)

@Schema(description = "보호자 KYC 가입 완료 응답")
data class GuardianBindResponse(
    @Schema(description = "보호 대상 미성년자 user_id", example = "1")
    val subjectUserId: Long,
    @Schema(description = "보호자 마스터 ID (동일 보호자 다중 자녀 인증 시 같은 값)", example = "1")
    val guardianId: Long,
    @Schema(description = "검증 완료", example = "true")
    val verified: Boolean,
)
