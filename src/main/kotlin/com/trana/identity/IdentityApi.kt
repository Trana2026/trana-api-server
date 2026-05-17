package com.trana.identity

import com.trana.common.exception.ProblemDetailResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.multipart.MultipartFile

@Tag(name = "Identity", description = "신원 인증 (KYC) — 신분증 OCR + 얼굴 비교")
interface IdentityApi {
    @Operation(
        summary = "신분증 OCR",
        description =
            "신분증 사진 → 이름/생년월일/성별/식별번호 해시 추출. " +
                "4종 지원: 주민등록증(ID_CARD) / 운전면허증(DRIVER_LICENSE) / 여권(PASSPORT) / 외국인등록증(ALIEN_REGISTRATION).",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "OCR 성공"),
            ApiResponse(
                responseCode = "500",
                description = "NCP 호출 실패 또는 인식 실패",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @PostMapping("/id-card", consumes = ["multipart/form-data"])
    fun recognizeIdCard(
        @RequestPart("file") file: MultipartFile,
    ): IdCardOcrResponse

    @Operation(
        summary = "얼굴 비교",
        description = "신분증 사진 + 셀카 → similarity(0.0~1.0) + isMatch(threshold 0.8 이상)",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "비교 성공"),
            ApiResponse(
                responseCode = "500",
                description = "NCP 호출 실패",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @PostMapping("/face-compare", consumes = ["multipart/form-data"])
    fun compareFaces(
        @RequestPart("cardImage") cardImage: MultipartFile,
        @RequestPart("faceImage") faceImage: MultipartFile,
    ): FaceCompareResponse

    @Operation(
        summary = "신분증 진위확인",
        description =
            "OCR 단계의 requestId로 행안부/경찰청 진위확인. " +
                "서버가 세션 캐시에서 평문 식별번호를 꺼내 NCP Verify API에 전달.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "진위확인 결과 (isValid)"),
            ApiResponse(
                responseCode = "400",
                description = "validation 실패 (requestId 누락 등)",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "500",
                description = "세션 만료/없음 또는 NCP 호출 실패",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @PostMapping("/verify-id-card")
    fun verifyIdCard(
        @RequestBody @Valid request: IdCardVerifyRequest,
    ): IdCardVerifyResponse
}
