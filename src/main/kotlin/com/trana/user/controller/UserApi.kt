package com.trana.user.controller

import com.trana.common.exception.ProblemDetailResponse
import com.trana.user.UserExamples
import com.trana.user.dto.MeResponse
import com.trana.user.dto.UpdateProfileRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus

@Tag(name = "User", description = "사용자")
interface UserApi {
    @Operation(
        summary = "본인 정보 조회",
        description = """
JWT subject(userId) 기준으로 본인 정보 반환. 마이페이지 화면 + 헤더 표시명에 활용.

응답 필드:
- 시스템: publicCode / status / ageGroup / guardianVerifiedAt
- 본인정보: name / birthDate / gender / phone / email
- 푸시 동의: pushEnabled (PATCH /v1/users/me/push-enabled 로 변경)

채움 정책:
- 성인 (ADULT): KYC SUCCESS 시 name/birthDate/gender/phone 모두 채움. email 은 KYC 흐름 미수집 → null
- 미성년 (MINOR, 보호자 인증 완료): name = 소셜 표시명 (provider 자동). birthDate/gender/phone 은 본인 KYC 없어 null
- 미성년 (MINOR, 보호자 인증 미완): guardianVerifiedAt=null. 마이페이지 진입 차단 또는 보호자 인증 유도 화면

미성년자 가입 완료 폴링은 guardianVerifiedAt 필드로 판정.
      """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [
                    Content(
                        schema = Schema(implementation = MeResponse::class),
                        examples = [
                            ExampleObject(name = "adult", value = UserExamples.ME_ADULT),
                            ExampleObject(name = "minor", value = UserExamples.ME_MINOR),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "토큰 없음 / 만료 / 위변조",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "사용자 없음 (USER_404)",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @GetMapping("/me")
    fun getMe(
        @Parameter(hidden = true) userId: Long,
    ): MeResponse

    @Operation(
        summary = "본인 정보 수정 (이메일 / 성별)",
        description = """
  마이페이지 profile 편집 — 이메일과 성별을 PATCH partial update.

  동작:
  - email: null / 미전송 시 변경 없음. 값 있으면 이메일 형식 검증 + UNIQUE 검증 → 세팅. 본인 기존 email 과 같으면 skip
  - gender: null / 미전송 시 변경 없음. MALE / FEMALE → 해당 값, NONE → user.gender=null ("미등록")
  - name / birthDate / phone 은 KYC 원본이라 수정 불가 (본 endpoint 대상 아님)

  응답: 수정 후 갱신된 MeResponse 반환 (GET /me 와 동일 형식).
          """,
        requestBody =
            io.swagger.v3.oas.annotations.parameters.RequestBody(
                content = [
                    Content(
                        schema = Schema(implementation = UpdateProfileRequest::class),
                        examples = [
                            ExampleObject(name = "email-only", value = UserExamples.UPDATE_PROFILE_EMAIL_ONLY),
                            ExampleObject(name = "gender-none", value = UserExamples.UPDATE_PROFILE_GENDER_NONE),
                            ExampleObject(name = "both", value = UserExamples.UPDATE_PROFILE_BOTH),
                        ],
                    ),
                ],
            ),
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "수정 성공",
                content = [
                    Content(
                        schema = Schema(implementation = MeResponse::class),
                        examples = [ExampleObject(name = "updated", value = UserExamples.ME_ADULT)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "이메일 형식 오류 또는 gender enum 유효하지 않음",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "토큰 없음 / 만료 / 위변조",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "사용자 없음 (USER_404)",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "이미 사용 중인 이메일 (USER_409_EMAIL_ALREADY_EXISTS)",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @PatchMapping("/me/profile")
    fun updateProfile(
        @Parameter(hidden = true) userId: Long,
        @Valid @RequestBody request: UpdateProfileRequest,
    ): MeResponse

    @Operation(
        summary = "회원 탈퇴",
        description = """
JWT subject(userId) 기준으로 회원 탈퇴 처리.

동작:
- status=WITHDRAWN + withdrawnAt 설정 (soft delete)
- 연관 데이터 (identity_verifications, user_consents, guardian_links)는 보존 (audit + 법적)
- access 토큰은 자연 만료 (15분) — 클라이언트가 로컬 토큰 폐기 필요

재가입:
- 성인: 같은 신분증으로 KYC 재진입 가능 (이전 SUCCESS verification은 ACTIVE 아닌 user 소유라 차단 안 됨)
- 미성년자: 같은 소셜 계정으로 재로그인 시 신규 user 발급 (이전 social 매핑 삭제 후 신규 생성)
          """,
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "탈퇴 성공"),
            ApiResponse(
                responseCode = "401",
                description = "토큰 없음 / 만료 / 위변조",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "사용자 없음 (USER_404)",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "이미 탈퇴됨 (USER_409_ALREADY_WITHDRAWN)",
                content = [Content(schema = Schema(implementation = ProblemDetailResponse::class))],
            ),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/me")
    fun withdraw(
        @Parameter(hidden = true) userId: Long,
    )
}
