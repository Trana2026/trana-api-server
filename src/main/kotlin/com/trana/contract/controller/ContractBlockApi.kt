package com.trana.contract.controller

import com.trana.user.dto.BlockUserResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus

@Tag(name = "Block", description = "사용자 차단 관리 (계약 상대 차단)")
@RequestMapping("/v1/contracts")
@SecurityRequirement(name = "bearerAuth")
interface ContractBlockApi {
    @Operation(
        summary = "계약 상대방 차단",
        description = """
계약 상세/카드에서 상대방을 차단한다. 서버가 해당 계약의 상대 사용자를 해석해 차단한다.

효과:
- 차단당한 사용자가 생성한 계약이 내 목록/상세에서 숨겨진다.
- 단, 양측 서명 완료(SIGNED/COMPLETED) 계약은 예외로 항상 노출된다.
- 멱등(이미 차단 상태면 그대로 성공). 상대 미확정(단독 초안) 계약은 400.
          """,
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{publicCode}/block")
    fun blockCounterparty(
        @Parameter(hidden = true) userId: Long,
        @PathVariable publicCode: String,
    ): BlockUserResponse
}
