package com.trana.common.util

import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * KST (Asia/Seoul) 공용 상수 — ZoneId + 자주 쓰는 3종 포맷터.
 *
 * @Scheduled(zone = "Asia/Seoul") 처럼 어노테이션 인자 (컴파일 상수 필요) 는
 * 여기 참조 불가 — 문자열 리터럴 유지.
 */
object KstFormatter {
    val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

    /** yyyy-MM-dd HH:mm:ss — Slack audit / 서버 로그 등 초 단위. */
    val LOG: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZONE)

    /** yyyy-MM-dd HH:mm — 사용자 대면 알림톡 등 분 단위. */
    val DISPLAY: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZONE)

    /** yyyyMMddHHmmss — PASS mobileOK 등 외부 API 요구 압축 형식. */
    val COMPACT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZONE)
}
