package com.trana.notification.adapter.ipinfo

/**
 * IP → 지역 조회 어댑터 (Anti-corruption layer).
 *
 * - 등록 시점 IP 를 넘겨서 city / country 조회 (예: "Seoul", "KR")
 * - 조회 실패 (rate limit / 사설 IP / API 장애 등) 시 null 반환 → 등록 자체는 정상 진행
 *
 * 활성 분기:
 * - `ipinfo-live` profile 꺼짐 (기본) → [MockIpInfoClient] — local/dev/test/prod 모두 기본 Mock (항상 null)
 * - `ipinfo-live` profile 켜짐 → [LiveIpInfoClient] (TRANA_IPINFO_TOKEN 필수)
 *
 * Live 조회 활성화: `SPRING_PROFILES_ACTIVE=dev,ipinfo-live` + `TRANA_IPINFO_TOKEN` env var 채움
 */
interface IpInfoClient {
    fun lookup(ip: String): IpLocationResult?
}

/**
 * IP → 지역 조회 결과.
 *
 * @param city ipinfo.io "city" 필드 (예: "Seoul"). 없으면 null (도시 미매핑 IP)
 * @param country ISO 3166-1 alpha-2 (예: "KR"). 없으면 null (사설 IP 등)
 */
data class IpLocationResult(
    val city: String?,
    val country: String?,
)
