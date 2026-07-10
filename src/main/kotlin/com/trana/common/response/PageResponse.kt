package com.trana.common.response

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page

/**
 * 페이징 응답 공통 wrapper.
 *
 * Spring [Page] 를 그대로 반환하면 내부 필드 (pageable / sort 등) 가 Jackson 직렬화되어
 * Swagger 스키마가 지저분해지고 향후 Spring 버전 업 시 필드 변경 리스크 있음. 통제된 필드만 노출.
 *
 * 사용 예:
 * ```
 * val page = notificationRepository.findAllByUserId(userId, pageable)
 * return PageResponse.from(page) { it.toResponse() }
 * ```
 */
@Schema(name = "PageResponse", description = "페이징 응답 공통 wrapper")
data class PageResponse<T>(
    @Schema(description = "현재 페이지 데이터")
    val content: List<T>,
    @Schema(description = "현재 페이지 번호 (0-based)", example = "0")
    val page: Int,
    @Schema(description = "페이지 크기", example = "20")
    val size: Int,
    @Schema(description = "전체 요소 수", example = "42")
    val totalElements: Long,
    @Schema(description = "전체 페이지 수", example = "3")
    val totalPages: Int,
    @Schema(description = "다음 페이지 존재 여부", example = "true")
    val hasNext: Boolean,
) {
    companion object {
        fun <E : Any, R : Any> from(
            page: Page<E>,
            mapper: (E) -> R,
        ): PageResponse<R> =
            PageResponse(
                content = page.content.map(mapper),
                page = page.number,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
                hasNext = page.hasNext(),
            )
    }
}
