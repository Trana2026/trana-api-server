package com.trana.common

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityScheme
import org.springframework.context.annotation.Configuration

/**
 * Swagger UI에 JWT Bearer 인증 버튼 추가.
 *
 * 효과:
 * - 자물쇠 아이콘 + Authorize 버튼 활성화
 * - 입력한 토큰이 모든 endpoint 호출에 자동 첨부 (Authorization: Bearer ...)
 * - 401 응답이 spec에 자동 등록 → "Undocumented" 사라짐
 */
@Configuration
@OpenAPIDefinition(
    security = [SecurityRequirement(name = "bearerAuth")],
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
)
class OpenApiConfig
