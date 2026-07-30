package com.trana.user.service

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * 부팅 시 1회 — 고유코드(share_code) 미발급 유저 백필.
 *
 * V14 가 컬럼을 nullable 로 추가했으므로 기존 유저는 코드가 없음.
 * share_code IS NULL 대상만 채우므로 멱등(재기동 안전). 전부 채워지면 no-op.
 * 전 유저 발급 확인 후 후속 마이그레이션에서 NOT NULL 승격 예정.
 */
@Component
class ShareCodeBackfillRunner(
    private val userService: UserService,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val count = userService.backfillShareCodes()
        if (count > 0) {
            log.info("[SHARE_CODE] 기존 유저 고유코드 백필 완료 — {}건 발급", count)
        }
    }
}
