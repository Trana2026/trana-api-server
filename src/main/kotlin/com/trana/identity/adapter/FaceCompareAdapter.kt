package com.trana.identity.adapter

/**
 * 얼굴 비교 어댑터.
 *
 * - 신분증 얼굴 ↔ 셀카 유사도 (0.0 ~ 1.0)
 * - 매칭 임계값(threshold)은 호출 측이 결정 (성인 KYC / 보호자 KYC 분리)
 * - 벤더 추상화 (현재 구현: NcpFaceCompareAdapter, 추후 KCB / Toss 등 교체 가능)
 */
interface FaceCompareAdapter {
    fun compareFaces(
        idCardImage: ImageInput,
        selfieImage: ImageInput,
        threshold: Double,
    ): FaceCompareResult
}
