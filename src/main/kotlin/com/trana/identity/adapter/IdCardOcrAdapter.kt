package com.trana.identity.adapter

/**
 * 신분증 OCR 어댑터.
 *
 * - 신분증 이미지 → 외부 노출용 result(hash만) + 내부 평문 sensitive data 분리 반환
 * - 벤더 추상화 (현재 구현: NcpIdCardAdapter, 추후 KCB / Toss 등 교체 가능)
 */
interface IdCardOcrAdapter {
    fun recognizeIdCard(image: ImageInput): IdCardOcrOutput
}
