package com.trana.common

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.customizers.OperationCustomizer
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
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
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
)
class OpenApiConfig {
    @Bean
    fun globalErrorResponseCustomizer(): OperationCustomizer =
        OperationCustomizer { operation, _ ->
            if (!operation.responses.containsKey("500")) {
                operation.responses.addApiResponse(
                    "500",
                    ApiResponse()
                        .description("서버 오류 (외부 API 장애 또는 처리되지 않은 예외)")
                        .content(
                            Content().addMediaType(
                                "application/problem+json",
                                MediaType().schema(
                                    Schema<Any>().`$ref`("#/components/schemas/ProblemDetailResponse"),
                                ),
                            ),
                        ),
                )
            }
            operation
        }

    @Bean
    fun pathOrderCustomizer(): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            val explicitOrder =
                listOf(
                    // Identity (성인 KYC 4-step)
                    "/v1/identity/id-card",
                    "/v1/identity/verify-id-card",
                    "/v1/identity/phone",
                    "/v1/identity/face-compare",
                    // Guardian KYC (Phase 6에서 채울 예정)
                    "/v1/identity/guardian/id-card",
                    "/v1/identity/guardian/verify-id-card",
                    "/v1/identity/guardian/face-compare",
                )
            val originalPaths = openApi.paths ?: return@OpenApiCustomizer
            val sorted = Paths()
            explicitOrder.forEach { path ->
                originalPaths[path]?.let { sorted.addPathItem(path, it) }
            }
            originalPaths.forEach { (path, item) ->
                if (path !in explicitOrder) sorted.addPathItem(path, item)
            }
            openApi.paths(sorted)
        }

    @Bean
    fun adultGroup(pathOrderCustomizer: OpenApiCustomizer): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .group("성인")
            .pathsToMatch(
                "/v1/consents",
                "/v1/terms/**",
                "/v1/identity/**",
                "/v1/users/**",
                "/v1/auth/refresh",
            ).pathsToExclude("/v1/identity/guardian/**")
            .addOpenApiCustomizer(pathOrderCustomizer)
            .build()

    @Bean
    fun minorGroup(pathOrderCustomizer: OpenApiCustomizer): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .group("미성년자")
            .pathsToMatch(
                "/v1/auth/**",
                "/v1/terms/**",
                "/v1/guardian/**",
                "/v1/identity/guardian/**",
                "/v1/users/**",
            ).addOpenApiCustomizer(pathOrderCustomizer)
            .build()
}
