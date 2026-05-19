package com.trana.identity.adapter.ncp

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * NCP CLOVA eKYC Verify API 응답 DTO.
 *
 * - result: "SUCCESS" | "FAILURE" (행안부/경찰청 진위확인 결과)
 * - code: 에러 코드 (실패 시)
 * - message: 상태 메시지
 * - inferDetailType: NCP가 검증한 타입 ("IC"|"DL"|"PP"|"AC") — 우리 idType과 매칭 확인 가능
 * - 무시: requestId, uid, timestamp, inferType → ignoreUnknown
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NcpIdCardVerifyResponse(
    val result: String?,
    val code: String?,
    val message: String?,
    val inferDetailType: String?,
)
