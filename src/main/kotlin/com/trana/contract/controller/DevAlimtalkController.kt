package com.trana.contract.controller

import com.trana.common.dev.DevProperties
import com.trana.common.web.WebUrlBuilder
import com.trana.contract.adapter.kakao.ContractCompletedMessage
import com.trana.contract.adapter.kakao.KakaoAlimtalkClient
import com.trana.contract.adapter.kakao.NewContractMessage
import com.trana.contract.adapter.kakao.ReceiverSignedMessage
import com.trana.contract.adapter.kakao.RevisionRequestedMessage
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

/**
 * 개발 전용 — 4 알림톡 template 실 발송 트리거 (매칭 검증 반복 편의).
 *
 * 실 계약 흐름 (create → share → sign) 없이 직접 KakaoAlimtalkClient 호출.
 * hardcode dummy data 사용 — 사용자 phone 만 파라미터.
 *
 * 보안:
 * - @Profile("!prod") — prod 에서 endpoint 자체 없음
 * - X-Dev-Token-Key 헤더 필수
 */
@Profile("!prod")
@RestController
@RequestMapping("/v1/dev/alimtalk")
@Tag(name = "Dev", description = "개발 전용 (local/dev profile)")
class DevAlimtalkController(
    private val devProperties: DevProperties,
    private val kakaoAlimtalkClient: KakaoAlimtalkClient,
    private val webUrlBuilder: WebUrlBuilder,
) {
    @Operation(
        summary = "알림톡 발송 트리거 (개발용)",
        description =
            "심사 원문 매칭 검증 반복 시 실 계약 흐름 없이 4 template 발송. " +
                "hardcode dummy data 사용 — 사용자 phone 만 파라미터. " +
                "X-Dev-Token-Key 헤더 필수 — 불일치 시 403.",
    )
    @PostMapping("/send")
    fun send(
        @RequestHeader(value = "X-Dev-Token-Key", required = false) providedKey: String?,
        @RequestParam type: AlimtalkType,
        @RequestParam phone: String,
    ) {
        if (providedKey != devProperties.tokenKey) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "X-Dev-Token-Key 헤더 검증 실패")
        }
        when (type) {
            AlimtalkType.NEW_CONTRACT -> {
                kakaoAlimtalkClient.sendNewContract(
                    NewContractMessage(
                        receiverPhone = phone,
                        receiverName = "임현진",
                        sellerName = "김테스트A",
                        contractTitle = DUMMY_TITLE,
                        price = DUMMY_PRICE,
                        invitationUrl = webUrlBuilder.contractInvitation(DUMMY_TOKEN),
                    ),
                )
            }

            AlimtalkType.RECEIVER_SIGNED -> {
                kakaoAlimtalkClient.sendReceiverSigned(
                    ReceiverSignedMessage(
                        creatorPhone = phone,
                        creatorName = "김테스트A",
                        receiverName = "임현진",
                        contractTitle = DUMMY_TITLE,
                        price = DUMMY_PRICE,
                        reviewUrl = webUrlBuilder.contractDetail(DUMMY_PUBLIC_CODE),
                    ),
                )
            }

            AlimtalkType.REVISION_REQUESTED -> {
                kakaoAlimtalkClient.sendRevisionRequested(
                    RevisionRequestedMessage(
                        creatorPhone = phone,
                        creatorName = "김테스트A",
                        contractTitle = DUMMY_TITLE,
                        requesterName = "임현진",
                        price = DUMMY_PRICE,
                        revisionReason = "가격: 100만원으로 조정\n상태: 액정 스크래치 있음",
                        reviewUrl = webUrlBuilder.contractDetail(DUMMY_PUBLIC_CODE),
                    ),
                )
            }

            AlimtalkType.COMPLETED -> {
                kakaoAlimtalkClient.sendCompleted(
                    ContractCompletedMessage(
                        recipientPhone = phone,
                        recipientName = "김테스트A",
                        contractTitle = DUMMY_TITLE,
                        price = DUMMY_PRICE,
                        completedAt = Instant.now(),
                        downloadUrl = webUrlBuilder.contractDetail(DUMMY_PUBLIC_CODE),
                    ),
                )
            }
        }
    }

    companion object {
        private const val DUMMY_TITLE = "테스트 아이폰 15 Pro 256GB"
        private const val DUMMY_PRICE = 1_200_000L
        private const val DUMMY_TOKEN = "TEST_INVITATION_TOKEN_21CHARS"
        private const val DUMMY_PUBLIC_CODE = "TESTCODE0001"
    }
}

enum class AlimtalkType {
    NEW_CONTRACT,
    RECEIVER_SIGNED,
    REVISION_REQUESTED,
    COMPLETED,
}
