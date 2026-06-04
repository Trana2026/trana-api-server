package com.trana.identity

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * NCP face compare 임계값 (refactor — yml/env override 가능).
 *
 * - adultThreshold: 성인 본인 KYC (앱). yml default 0.5
 * - guardianThreshold: 보호자 KYC (web). yml default 0.5 (구 0.35 → 강화 요청)
 *
 * 정책:
 * - **yml default = prod 운영값**. prod 환경변수 override 금지 (deny by policy)
 * - dev Railway 환경변수만 override 허용 (테스트 / fine-tune 목적)
 * - 변경 시 Railway dashboard 로그 = 1차 audit trail. 영구 정책 변경은 yml + PR
 *
 * 환경변수 명:
 * - `TRANA_IDENTITY_FACE_MATCH_ADULT_THRESHOLD`
 * - `TRANA_IDENTITY_FACE_MATCH_GUARDIAN_THRESHOLD`
 */
@ConfigurationProperties(prefix = "trana.identity.face-match")
data class IdentityFaceMatchProperties(
    val adultThreshold: Double,
    val guardianThreshold: Double,
)
