package com.trana.identity.adapter

/**
 * 신분증 진위확인 어댑터.
 *
 * - 행안부/경찰청 API 호출 (NCP Verify API 위임)
 * - 4종 신분증 지원 (input.idType 으로 어댑터 내부에서 분기)
 * - 벤더 추상화 (현재 구현: NcpIdCardVerifyAdapter, 추후 교체 가능)
 */
interface IdCardVerifyAdapter {
    fun verify(input: IdCardVerifyInput): IdCardVerifyResult
}
