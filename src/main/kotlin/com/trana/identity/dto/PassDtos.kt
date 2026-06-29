package com.trana.identity.dto

import io.swagger.v3.oas.annotations.media.Schema
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
