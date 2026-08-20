package com.trana.common.demo

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 데모 모드 조회 — public. 앱 인트로가 진입 시 조회해 테스트 로그인 UI 활성 여부 결정.
 *
 * - 인증 불필요(비로그인 인트로에서 호출). SecurityConfig permitAll.
 * - 전 프로파일에서 응답(값은 환경변수 trana.demo.enabled).
 */
@RestController
@RequestMapping("/v1/demo")
@Tag(name = "Demo", description = "데모 모드 (심사 대응)")
class DemoModeController(
    private val demoProperties: DemoProperties,
) {
    @Operation(
        summary = "데모 모드 활성 여부 조회",
        description = "enabled=true 면 앱이 인트로 테스트 로그인(롱프레스) UI 를 노출한다. 서버 값 하나로 여닫음.",
    )
    @GetMapping("/mode")
    fun mode(): DemoModeResponse = DemoModeResponse(enabled = demoProperties.enabled)
}
