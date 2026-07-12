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
    fun sortingCustomizer(): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            sortTags(openApi)
            sortPaths(openApi)
        }

    private fun sortTags(openApi: io.swagger.v3.oas.models.OpenAPI) {
        val order =
            listOf(
                "Terms",
                "Consent",
                "Auth",
                "Identity PASS",
                "Guardian Identity PASS",
                "Guardian",
                "User",
                "User Consent",
                "User Inquiry",
                "User Preference",
                "Trust Score",
                "Device Token",
                "Notification",
                "Contract Draft",
                "Contract Attachment",
                "Contract AI Extraction",
                "Contract Lifecycle",
                "Contract PDF",
                "Contract Invitation",
                "Contract Dispute",
                "Contract Cancellation",
            )
        val devTagName = "Dev"
        val originalTags = openApi.tags?.toList().orEmpty()
        val sorted = mutableListOf<io.swagger.v3.oas.models.tags.Tag>()
        order.forEach { name -> originalTags.find { it.name == name }?.let { sorted.add(it) } }
        originalTags.forEach { if (it.name !in order && it.name != devTagName) sorted.add(it) }
        originalTags.find { it.name == devTagName }?.let { sorted.add(it) }
        openApi.tags = sorted
    }

    private fun sortPaths(openApi: io.swagger.v3.oas.models.OpenAPI) {
        val order =
            listOf(
                // PASS 표준창
                "/v1/identity/pass/req-client-info",
                "/v1/identity/pass/return",
                "/v1/identity/guardian/pass/req-client-info",
                // 계약 첨부 (presign → register → delete)
                "/v1/contracts/{publicCode}/attachments/presign",
                "/v1/contracts/{publicCode}/attachments",
                "/v1/contracts/{publicCode}/attachments/{attachmentId}",
            )
        val originalPaths = openApi.paths ?: return
        val sorted = Paths()
        order.forEach { path -> originalPaths[path]?.let { sorted.addPathItem(path, it) } }
        originalPaths.forEach { (path, item) -> if (path !in order) sorted.addPathItem(path, item) }
        openApi.paths(sorted)
    }

    @Bean
    fun defaultGroup(sortingCustomizer: OpenApiCustomizer): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .group("전체")
            .pathsToMatch("/v1/**")
            .addOpenApiCustomizer(sortingCustomizer)
            .build()
}
