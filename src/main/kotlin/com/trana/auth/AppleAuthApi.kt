package com.trana.auth

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

@Tag(name = "Apple Sign In", description = "Apple Sign In OAuth callback (Android web flow)")
interface AppleAuthApi {
    @Operation(
        summary = "Apple OAuth form_post callback (Android web flow)",
        description = """
Apple Authorization Server 가 form_post 로 POST 하는 endpoint (Apple Server-to-Server, 인증 불필요).

흐름:
1. Flutter Android app 이 Apple authorize URL 로 redirect (Custom Tab)
2. 사용자 Apple 로그인 + 동의
3. Apple 이 이 endpoint 로 form_post POST (code/state/id_token/user)
4. 백엔드가 받아 Flutter Android intent URL 로 302 redirect → Flutter app 이 deeplink intercept
5. Flutter app 이 id_token 추출 → /v1/auth/social/sign-in (provider=APPLE) 호출 → 검증/로그인

이 endpoint 는 **protocol bridge** — id_token 검증 안 함 (검증 시점은 /v1/auth/social/sign-in).
iOS native 흐름은 이 endpoint 안 거침 (직접 /v1/auth/social/sign-in).
  """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "302",
                description = "Flutter Android intent URL 로 redirect (Location 헤더)",
            ),
        ],
    )
    @PostMapping("/callback", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun callback(
        @Parameter(description = "OAuth authorization code")
        @RequestParam(required = false)
        code: String?,
        @Parameter(description = "CSRF state token (클라이언트가 보낸 값 그대로)")
        @RequestParam(required = false)
        state: String?,
        @Parameter(description = "OIDC id_token JWT")
        @RequestParam("id_token", required = false)
        idToken: String?,
        @Parameter(description = "최초 로그인 시 사용자 객체 JSON (name/email)")
        @RequestParam(required = false)
        user: String?,
    ): ResponseEntity<Void>

    @Operation(
        summary = "Apple Sign In Server-to-Server notification",
        description = """
Apple 가 다음 이벤트 발생 시 호출하는 endpoint (Apple Server-to-Server, 인증 불필요):
- email-disabled / email-enabled: 사용자가 private relay email forwarding 끔/켬
- consent-revoked: 사용자가 Apple ID 설정에서 우리 앱 권한 해제
- account-delete: Apple ID 계정 자체 삭제

처리:
- payload (signed JWT) 를 Apple JWKS 로 검증 (iss=appleid.apple.com, aud=Services ID)
- consent-revoked / account-delete → user soft delete (UserService.withdraw)
- email-disabled / email-enabled → audit 기록만
- 멱등성: AlreadyWithdrawn 시 무시 (Apple notification 재전송 가능)

Apple 정책: 200 외 응답 시 재전송. 검증 실패도 항상 200 (audit 기록).
  """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "처리 완료 (성공 / 무시 모두 200)",
            ),
        ],
    )
    @PostMapping("/revoke-notification", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun revokeNotification(
        @org.springframework.web.bind.annotation.RequestBody
        request: AppleRevokeNotification,
    ): ResponseEntity<Void>
}
