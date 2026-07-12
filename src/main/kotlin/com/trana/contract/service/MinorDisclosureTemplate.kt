package com.trana.contract.service

/**
 * 미성년자와 거래하는 상대방(성인)에게 서명 전 표시하는 위험 고지 문구 (Figma 원문 정확 반영).
 *
 * - 문구 자체는 코드 상수 (버전 관리). DB minor_disclosure_confirmations 에는 버전 문자열만 저장
 * - 문구 변경 시 반드시 LATEST_VERSION bump + 이전 버전은 기록 유지 (분쟁 audit 재현)
 * - 프론트 표시 = 여기 원문 정확 매칭 필수 (분쟁 시 고지 입증)
 * - 이용약관 제32조 제2항 의무
 */
object MinorDisclosureTemplate {
    const val LATEST_VERSION = "v1"

    /** 화면 상단 제목 */
    const val TITLE = "미성년자와의 거래입니다"

    /**
     * 본문 항목 5개 — 순서대로 프론트에 노출.
     * 개행 (`\n`) 원문 그대로 유지.
     */
    val ITEMS: List<String> =
        listOf(
            "거래 상대방은 만 19세 미만의 미성년자입니다.",
            "이 계약은 미성년자 본인 또는 보호자가 취소할 수 있습니다.",
            "취소되면 이미 지급한 대금을 전부 돌려받지 못할 수 있습니다.\n미성년자는 남아있는 이익만 반환하면 되기 때문입니다.",
            "서명하시면 위 내용을 확인한 것으로 보아,\n이후 계약을 철회할 수 없습니다.",
            "확인하신 사실과 시각이 기록됩니다.",
        )
}
