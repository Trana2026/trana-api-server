package com.trana.identity.adapter.pass

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 드림시큐리티 mobileOK V3 표준창 (PASS) 설정.
 *
 * - keyInfoBase64: mok_keyInfo.dat 파일을 base64 인코딩한 환경변수 값 (앱 시작 시 1회 복호화 → 메모리 캐시)
 * - keyInfoPassword: 키 파일 복호화용 mobileOK 비밀번호 (서비스 등록 시 발급)
 * - usageCode: 서비스 이용 코드 (01005 = 본인확인용)
 * - serviceType: 이용상품 코드 (telcoAuth = 휴대폰본인확인)
 * - returnUrl: 표준창 결과 수신 백엔드 endpoint (절대 URL, mobileOK 콘솔 사전 등록 필요)
 * - mobileOkBaseUrl: 검증결과 요청 base URL (dev: scert / prod: cert)
 */
@ConfigurationProperties(prefix = "trana.identity.pass")
data class PassProperties(
    val keyInfoBase64: String,
    val keyInfoPassword: String,
    val usageCode: String = "01005",
    val serviceType: String = "telcoAuth",
    val returnUrl: String,
    val mobileOkBaseUrl: String,
    val resultRedirectUrl: String,
) {
    val resultRequestUrl: String
        get() = "$mobileOkBaseUrl/gui/service/v1/result/request"
}
