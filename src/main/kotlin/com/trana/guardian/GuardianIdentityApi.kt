package com.trana.guardian

import com.trana.common.exception.ProblemDetailResponse
import com.trana.identity.FaceCompareResponse
import com.trana.identity.IdCardOcrResponse
import com.trana.identity.IdCardVerifyResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.multipart.MultipartFile

@Tag(name = "Guardian Identity", description = "보호자 KYC (미성년자 가입 보호자 인증) — 토큰 인증, JWT X")
interface GuardianIdentityApi {
    @Operation(
        summary = "보호자 신분증 OCR",
        description =
            "보호자 매칭 토큰으로 인증. 미성년자 신분증이면 OCR 단계에서 거부 (403 NotAdult). " +
                "본인 KYC와 동일 흐름.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "OCR 성공"),
            ApiResponse(
                responseCode = "403",
                description = "보호자가 성인이 아님 (GUARDIAN_403_NOT_ADULT)",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "토큰 없음 (GUARDIAN_404_LINK)",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "410",
                description = "토큰 만료/사용/취소 (GUARDIAN_410_LINK)",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @PostMapping("/id-card", consumes = ["multipart/form-data"])
    fun recognizeIdCard(
        @RequestPart("file") file: MultipartFile,
        @RequestParam("token") token: String,
    ): IdCardOcrResponse

    @Operation(
        summary = "보호자 신분증 진위확인",
        description = "OCR 단계의 requestId + 매칭 토큰. NCP 행안부/경찰청 진위확인.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "진위확인 결과"),
            ApiResponse(
                responseCode = "404",
                description = "토큰 없음 (GUARDIAN_404_LINK)",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "410",
                description = "토큰 만료/사용/취소 (GUARDIAN_410_LINK)",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @PostMapping("/verify-id-card")
    fun verifyIdCard(
        @RequestBody @Valid request: GuardianIdCardVerifyRequest,
    ): IdCardVerifyResponse

    @Operation(
        summary = "보호자 얼굴 비교",
        description =
            "셀카 + requestId + 매칭 토큰. SUCCESS 시 guardians/guardian_relations INSERT + " +
                "미성년자 user.guardian_verified_at 설정 + 토큰 markUsed.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "비교 성공"),
            ApiResponse(
                responseCode = "404",
                description = "세션 또는 토큰 없음",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "410",
                description = "세션/토큰 만료",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @PostMapping("/face-compare", consumes = ["multipart/form-data"])
    fun compareFaces(
        @RequestPart("faceImage") faceImage: MultipartFile,
        @RequestParam("requestId") requestId: String,
        @RequestParam("token") token: String,
    ): FaceCompareResponse
}
