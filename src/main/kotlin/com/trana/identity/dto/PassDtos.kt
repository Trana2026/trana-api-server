package com.trana.identity.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.UUID

@Schema(description = "PASS 표준창 요청 토큰 발급 요청 — 약관 동의 단계의 signupSessionId 필수")
data class PassReqClientInfoRequest(
    @field:NotNull
    @Schema(
        description = "약관 동의 단계에서 받은 signupSessionId (30분 TTL)",
        example = "20a4b2c9-1f3e-4a7d-9c1b-8e5f2a3b4c5d",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val signupSessionId: UUID,
)

@Schema(
    description = """
  MOKReqClientInfo — mobileOK V3 표준창의 MOBILEOK.process(MOKReqClientInfoUrl, ...) 응답 형식 그대로.

  trana-web 에서 이 응답을 받아 MOBILEOK.process() callback 으로 그대로 전달.
  """,
)
data class MOKReqClientInfoResponse(
    @Schema(description = "이용기관 서비스 ID (mok_keyInfo.dat 안에 포함)")
    val serviceId: String,
    @Schema(description = "RSA-OAEP 암호화된 본인확인 거래 요청 정보 (Base64)")
    val encryptReqClientInfo: String,
    @Schema(description = "이용상품 코드 — telcoAuth (휴대폰본인확인)")
    val serviceType: String,
    @Schema(description = "서비스 이용 코드 — 01005 (본인확인용)")
    val usageCode: String,
    @Schema(description = "결과 수신 타입 — 고정 MOKToken")
    val retTransferType: String,
    @Schema(description = "표준창 결과 수신 endpoint URL (백엔드 callback)")
    val returnUrl: String,
    @Schema(description = "암호화 버전 — 고정 V2")
    val encryptVersion: String,
)

@Schema(
    description = """
  PASS 표준창 검증 결과 — purpose 로 SIGNUP / GUARDIAN 분기.

  V3 표준창 spec: 백엔드가 /v1/identity/pass/return POST 처리 후 JSON body 반환 → 표준창이 MOBILEOK.process 의 callback 함수 인자로 그대로 전달. 프론트는 JSON.parse(payload) 로 받음.

  302 redirect 사용 X — V3 표준창은 returnUrl 응답을 callback payload 로 해석하므로 redirect 를 따라가지 않음.
      """,
)
sealed interface PassReturnResponse {
    @get:Schema(description = "응답 분기 식별자 — \"SIGNUP\" / \"GUARDIAN\"")
    val purpose: String

    @Schema(description = "성인/미성년 본인 가입 SUCCESS — JWT 발급 + publicCode")
    data class Signup(
        override val purpose: String = "SIGNUP",
        @Schema(description = "JWT access token (TTL 15분)")
        val accessToken: String,
        @Schema(description = "JWT refresh token (TTL 5년, PASS 도입 후 정책)")
        val refreshToken: String,
        @Schema(description = "public code (12자 jnanoid) — 사기 조회 / 안내용")
        val publicCode: String,
        @Schema(description = "true = 미성년 + 보호자 인증 미완료 → 프론트는 보호자 링크 발급 흐름으로 라우팅")
        val requiresGuardian: Boolean,
    ) : PassReturnResponse

    @Schema(description = "보호자 인증 SUCCESS — 자녀 publicCode 안내")
    data class Guardian(
        override val purpose: String = "GUARDIAN",
        @Schema(description = "고정 \"success\" (실패는 ProblemDetail 4xx/5xx 로 별도 응답)")
        val status: String,
        @Schema(description = "자녀 publicCode — 보호자 결과 페이지에서 자녀 식별 표시")
        val minorPublicCode: String,
    ) : PassReturnResponse
}

@Schema(description = "보호자 PASS 표준창 요청 토큰 발급 — 자녀가 발급한 GuardianLink 토큰 필수")
data class PassGuardianReqClientInfoRequest(
    @field:NotBlank
    @Schema(
        description = "GuardianLink 토큰 (jnanoid 21자) — 자녀의 POST /v1/guardian/links 응답에 포함",
        example = "V1StGXR8_Z5jdHi6B-myT",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val token: String,
)
