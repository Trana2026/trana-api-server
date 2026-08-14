package com.trana.user.controller

import com.trana.user.dto.BlockedUserView
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus

@Tag(name = "Block", description = "사용자 차단 관리 (목록 / 해제)")
@RequestMapping("/v1/blocks")
@SecurityRequirement(name = "bearerAuth")
interface UserBlockApi {
    @Operation(
        summary = "내가 차단한 사용자 목록",
        description = "설정 화면용. 차단 시각 최신순.",
    )
    @GetMapping
    fun listBlocked(
        @Parameter(hidden = true) userId: Long,
    ): List<BlockedUserView>

    @Operation(
        summary = "차단 해제",
        description = "상대 고유코드(shareCode) 기준 해제. 이미 해제 상태여도 204(멱등).",
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{shareCode}")
    fun unblock(
        @Parameter(hidden = true) userId: Long,
        @PathVariable shareCode: String,
    )
}
