package com.trana.identity.adapter

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * NCP CLOVA eKYC Face Compare API 응답 DTO.
 *
 * 핵심 필드만 추출:
 * - result: 처리 상태 ("SUCCESS" | "FAILED" 등)
 * - message: 상태 메시지 (실패 시 사유)
 * - similarity: 유사도 0.0 ~ 1.0 (도메인 threshold 판정용)
 * - similarityPercentage: 0 ~ 100 (참고용, 사람이 보기 편한 형태)
 *
 * 무시: requestId, uid, timestamp, faces[].boundingPoly/landmark/feature(512-dim)/
 * attributes/alignedImage → ignoreUnknown (필요 시 별도 어댑터에서 활용)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NcpFaceCompareResponse(
    val result: String?,
    val message: String?,
    val similarity: Double?,
    val similarityPercentage: Double?,
)
