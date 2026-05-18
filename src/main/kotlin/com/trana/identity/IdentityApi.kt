package com.trana.identity

import com.trana.common.exception.ProblemDetailResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.multipart.MultipartFile

@Tag(name = "Identity", description = "신원 인증 (KYC) — 신분증 OCR + 얼굴 비교")
@SecurityRequirement(name = "bearerAuth")
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
        @Parameter(hidden = true) userId: Long?,
        @RequestPart("file") file: MultipartFile,
    ): IdCardOcrResponse

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

    @Operation(
        summary = "얼굴 비교",
        description =
            "셀카 + requestId → similarity(0.0~1.0) + isMatch. " +
                "신분증 사진은 OCR 단계에서 서버가 보관 중 (별도 업로드 X).",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "비교 성공"),
            ApiResponse(
                responseCode = "404",
                description = "requestId 세션 없음",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "410",
                description = "requestId 세션 만료",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "500",
                description = "NCP 호출 실패 또는 신분증 사진 S3 조회 실패",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @PostMapping("/face-compare", consumes = ["multipart/form-data"])
    fun compareFaces(
        @RequestParam("requestId") requestId: String,
        @RequestPart("faceImage") faceImage: MultipartFile,
    ): FaceCompareResponse
}
