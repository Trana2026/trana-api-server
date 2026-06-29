package com.trana.identity.service

import com.trana.audit.AuditEvent
import com.trana.audit.AuditLogger
import com.trana.common.security.JwtProvider
import com.trana.identity.adapter.pass.PassCryptoUtil
import com.trana.identity.adapter.pass.PassMobileOkClient
import com.trana.identity.adapter.pass.PassProperties
import com.trana.identity.adapter.pass.PassResultDecryptor
import com.trana.identity.adapter.pass.toBirthDate
import com.trana.identity.adapter.pass.toGender
import com.trana.identity.entity.VerificationPurpose
import com.trana.identity.entity.VerificationStatus
import com.trana.identity.repository.IdentityVerificationRepository
import com.trana.terms.service.ConsentService
import com.trana.user.entity.AgeGroup
import com.trana.user.entity.UserStatus
import com.trana.user.repository.UserRepository
import com.trana.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.Period

/**
 * PASS (mobileOK V3 표준창) 본인확인 결과 콜백 처리.
 *
 * 흐름:
 * 1. 표준창 → POST data=URLEncoded({"encryptMOKKeyToken": "..."})
 * 2. URL decode + JSON parse → encryptMOKKeyToken
 * 3. mobileOK /gui/service/v1/result/request 호출 (5초 TTL 안에)
 * 4. encryptMOKResult RSA-OAEP + AES-CBC 복호화 + SHA-256 무결성 검증 → PassResultPayload
 * 5. clientTxId 매핑 PENDING verification 조회
 * 6. ci → SHA-256 → ci_hash 계산
 * 7. birthday → ageGroup (>=19 ADULT / <19 MINOR)
 * 8. 기존 ACTIVE user 매칭 (ci_hash) — 있으면 재로그인, 없으면 신규 생성
 * 9. verification.markPassSuccess 백필 + status SUCCESS
 * 10. consents.backfillUserId
 * 11. JWT access + refresh 발급
 * 12. trana-web 결과 페이지로 302 redirect (fragment 으로 token + publicCode 전달)
 */
@Service
@Transactional
class PassReturnService(
    private val mobileOkClient: PassMobileOkClient,
    private val resultDecryptor: PassResultDecryptor,
    private val verificationRepository: IdentityVerificationRepository,
    private val userRepository: UserRepository,
    private val userService: UserService,
    private val consentService: ConsentService,
    private val jwtProvider: JwtProvider,
    private val auditLogger: AuditLogger,
    private val properties: PassProperties,
    private val objectMapper: ObjectMapper,
) {
    @Suppress("LongMethod")
    fun handleReturn(rawFormData: String): String {
        val token = extractEncryptMOKKeyToken(rawFormData)

        val mokResponse = mobileOkClient.requestResult(token)
        check(mokResponse.resultCode == MOBILE_OK_SUCCESS) {
            "mobileOK result/request 실패: ${mokResponse.resultCode} ${mokResponse.resultMsg}"
        }
        val encryptedResult = checkNotNull(mokResponse.encryptMOKResult) { "encryptMOKResult null" }

        val payload = resultDecryptor.decrypt(encryptedResult)

        val verification =
            verificationRepository.findByClientTxId(payload.clientTxId)
                ?: error("clientTxId ${payload.clientTxId} 매핑 verification 없음")
        check(verification.status == VerificationStatus.PENDING) {
            "verification 상태 PENDING 아님: ${verification.status}"
        }
        check(verification.purpose == VerificationPurpose.SIGNUP) {
            "본인 SIGNUP verification 만 처리 (Guardian PASS 는 PASS-6)"
        }

        val ciHash = sha256Hex(payload.ci)
        val birthDate = payload.toBirthDate()
        val ageGroup = determineAgeGroup(birthDate)
        val gender = payload.toGender()

        val user =
            userRepository.findFirstByCiHashAndStatus(ciHash, UserStatus.ACTIVE)
                ?: userService.createFromPass(
                    ciHash = ciHash,
                    name = payload.userName,
                    birthDate = birthDate,
                    gender = gender,
                    phone = payload.userPhone,
                    ageGroup = ageGroup,
                )
        val userId = checkNotNull(user.id) { "User id null" }

        verification.markPassSuccess(
            ciHash = ciHash,
            name = payload.userName,
            birthDate = birthDate,
            gender = gender,
            phone = payload.userPhone,
            boundUserId = userId,
        )
        verification.signupSessionId?.let { consentService.backfillUserId(it, userId) }

        val accessToken = jwtProvider.createAccessToken(userId)
        val refreshToken = jwtProvider.createRefreshToken(userId)

        auditLogger.log(
            eventType = AuditEvent.IDENTITY_SIGNUP_COMPLETED,
            actorUserId = userId,
            entityType = "USER",
            entityId = userId,
            metadata = mapOf("source" to "PASS", "ageGroup" to ageGroup.name),
        )

        val requiresGuardian = (ageGroup == AgeGroup.MINOR && user.guardianVerifiedAt == null)
        return buildRedirectUrl(accessToken, refreshToken, user.publicCode, requiresGuardian)
    }

    private fun extractEncryptMOKKeyToken(rawFormData: String): String {
        val decodedJson = URLDecoder.decode(rawFormData, StandardCharsets.UTF_8)
        val parsed = objectMapper.readValue(decodedJson, Map::class.java)
        return checkNotNull(parsed["encryptMOKKeyToken"] as? String) { "encryptMOKKeyToken null" }
    }

    private fun determineAgeGroup(birthDate: LocalDate): AgeGroup {
        val age = Period.between(birthDate, LocalDate.now()).years
        return if (age >= ADULT_AGE) AgeGroup.ADULT else AgeGroup.MINOR
    }

    private fun sha256Hex(value: String): String =
        PassCryptoUtil
            .sha256(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun buildRedirectUrl(
        accessToken: String,
        refreshToken: String,
        publicCode: String,
        requiresGuardian: Boolean,
    ): String {
        val params =
            listOf(
                "accessToken" to accessToken,
                "refreshToken" to refreshToken,
                "publicCode" to publicCode,
                "requiresGuardian" to requiresGuardian.toString(),
            ).joinToString("&") { (k, v) ->
                "$k=${URLEncoder.encode(v, StandardCharsets.UTF_8)}"
            }
        return "${properties.resultRedirectUrl}#$params"
    }

    companion object {
        private const val MOBILE_OK_SUCCESS = "2000"
        private const val ADULT_AGE = 19
    }
}
