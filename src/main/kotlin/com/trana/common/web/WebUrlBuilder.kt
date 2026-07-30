package com.trana.common.web

import com.trana.contract.ContractWebProperties
import com.trana.guardian.GuardianProperties
import org.springframework.stereotype.Component

/**
 * Web URL 통합 빌더 (refactor j/hh).
 *
 * - Guardian 도메인 URL (보호자 web) — guardianProperties.webBaseUrl 기반
 * - Contract 도메인 URL (모바일 앱 / 계약 web) — contractWebProperties.baseUrl 기반
 * - 이전 INVITATION_BASE_URL 하드코딩 + 4 곳 분산 빌딩 통합 → dev/prod 분기 가능
 */
@Component
class WebUrlBuilder(
    private val guardianProperties: GuardianProperties,
    private val contractWebProperties: ContractWebProperties,
) {
    /** 가입 단계 보호자 KYC 진입 URL (SIGNUP 토큰). */
    fun guardianSignupVerify(token: String): String = "${guardianProperties.webBaseUrl}/verify/$token"

    /** 계약 상세 진입 URL (publicCode). */
    fun contractDetail(publicCode: String): String = "${contractWebProperties.baseUrl}/contracts/$publicCode"

    /** 계약 PDF 다운로드 URL (publicCode). */
    fun contractPdf(publicCode: String): String = "${contractWebProperties.baseUrl}/contracts/$publicCode/pdf"

    /** 계약 초대 진입 URL (invitation 토큰). */
    fun contractInvitation(token: String): String = "${contractWebProperties.baseUrl}/contracts/invitations/$token"

    // ── 알림톡 AL 버튼 앱 스킴 URL (linkAnd/linkIos). 웹 경로와 1:1, deeplink.md 참조 ──

    /** 계약 상세 앱 스킴 URL. */
    fun contractDetailApp(publicCode: String): String = "${contractWebProperties.appLinkBase}/contracts/$publicCode"

    /** 계약 초대 앱 스킴 URL. */
    fun contractInvitationApp(token: String): String =
        "${contractWebProperties.appLinkBase}/contracts/invitations/$token"

    /** 앱 홈 스킴 URL (특정 계약 접근 불가 시 홈으로 — 예: 취소 확정 후). */
    fun homeApp(): String = "${contractWebProperties.appLinkBase}/home"

    /** 홈(웹) 폴백 URL — AL 버튼 linkMo/linkPc 용. */
    fun home(): String = contractWebProperties.baseUrl
}
