package com.trana.identity.service

import com.trana.audit.AuditEvent
import com.trana.audit.AuditLogger
import com.trana.common.security.JwtProvider
import com.trana.guardian.GuardianProperties
import com.trana.guardian.entity.Guardian
import com.trana.guardian.repository.GuardianRepository
import com.trana.guardian.service.GuardianLinkService
import com.trana.identity.adapter.pass.PassCryptoUtil
import com.trana.identity.adapter.pass.PassMobileOkClient
import com.trana.identity.adapter.pass.PassProperties
import com.trana.identity.adapter.pass.PassResultDecryptor
import com.trana.identity.adapter.pass.PassResultPayload
import com.trana.identity.adapter.pass.toBirthDate
import com.trana.identity.adapter.pass.toGender
import com.trana.identity.entity.IdentityVerification
import com.trana.identity.entity.VerificationPurpose
import com.trana.identity.entity.VerificationStatus
import com.trana.identity.repository.IdentityVerificationRepository
import com.trana.notification.service.NotificationDispatchService
import com.trana.terms.service.ConsentService
import com.trana.user.entity.AgeGroup
import com.trana.user.entity.Gender
import com.trana.user.entity.UserStatus
import com.trana.user.repository.UserRepository
import com.trana.user.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.Period

/**
 * PASS 표준창 결과 콜백 처리 — SIGNUP / GUARDIAN purpose 분기.
 *
 * 공통 흐름:
 * 1. form data 디코드 + JSON parse → encryptMOKKeyToken
 * 2. mobileOK /result/request 호출 (5초 TTL)
 * 3. encryptMOKResult 복호화 + 무결성 검증
 * 4. clientTxId 매핑 PENDING verification 조회 + purpose 분기
 *
 * SIGNUP: 자녀/성인 본인 가입 — user create/match + JWT + trana-web redirect
 * GUARDIAN: 보호자 인증 — Guardian upsert + 자녀 markGuardianVerified + 푸시 + trana-web-guardian redirect
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
    private val guardianRepository: GuardianRepository,
    private val guardianLinkService: GuardianLinkService,
    private val notificationDispatchService: NotificationDispatchService,
    private val jwtProvider: JwtProvider,
    private val auditLogger: AuditLogger,
    private val properties: PassProperties,
    private val guardianProperties: GuardianProperties,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

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

        return when (verification.purpose) {
            VerificationPurpose.SIGNUP -> handleSelfSuccess(verification, payload)
            VerificationPurpose.GUARDIAN -> handleGuardianSuccess(verification, payload)
        }
    }

    private fun handleSelfSuccess(
        verification: IdentityVerification,
        payload: PassResultPayload,
    ): String {
        val ciHash = sha256Hex(payload.ci)
        val birthDate = payload.toBirthDate()
        val gender = payload.toGender()
        val ageGroup = determineAgeGroup(birthDate)

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
        return buildSelfRedirectUrl(accessToken, refreshToken, user.publicCode, requiresGuardian)
    }

    @Suppress("LongMethod")
    private fun handleGuardianSuccess(
        verification: IdentityVerification,
        payload: PassResultPayload,
    ): String {
        val subjectUserId = checkNotNull(verification.subjectUserId) { "GUARDIAN: subjectUserId null" }
        val guardianLinkToken = checkNotNull(verification.guardianLinkToken) { "GUARDIAN: guardianLinkToken null" }
        val minor = userService.getById(subjectUserId)
        check(minor.guardianVerifiedAt == null) { "이미 보호자 인증 완료된 미성년자" }

        val ciHash = sha256Hex(payload.ci)
        val birthDate = payload.toBirthDate()
        val gender = payload.toGender()

        val guardian =
            upsertGuardianByCiHash(
                ciHash = ciHash,
                name = payload.userName,
                birthDate = birthDate,
                gender = gender,
            )
        val guardianId = checkNotNull(guardian.id) { "Guardian id null" }

        verification.markPassGuardianSuccess(
            ciHash = ciHash,
            name = payload.userName,
            birthDate = birthDate,
            gender = gender,
            phone = payload.userPhone,
            boundGuardianId = guardianId,
        )
        guardianLinkService.markUsed(guardianLinkToken)
        minor.markGuardianVerified()
        userRepository.save(minor)

        runCatching {
            notificationDispatchService.sendToUser(
                userId = subjectUserId,
                title = "보호자 인증이 완료됐어요",
                body = "이제 Trana 를 사용할 수 있어요",
                deeplink = "trana://home",
                data = mapOf("event" to "GUARDIAN_PASS_SUCCESS"),
            )
        }.onFailure { ex ->
            log.warn("[FCM] 보호자 PASS SUCCESS 푸시 실패 — silent minorId={}", subjectUserId, ex)
        }

        auditLogger.log(
            eventType = AuditEvent.GUARDIAN_VERIFIED_COMPLETED,
            actorUserId = subjectUserId,
            entityType = "USER",
            entityId = subjectUserId,
            metadata = mapOf("guardianId" to guardianId, "source" to "PASS"),
        )

        return buildGuardianRedirectUrl(minor.publicCode)
    }

    private fun upsertGuardianByCiHash(
        ciHash: String,
        name: String,
        birthDate: LocalDate,
        gender: Gender,
    ): Guardian {
        val existing = guardianRepository.findByCiHash(ciHash)
        return existing ?: guardianRepository.save(
            Guardian(
                identifierHash = null,
                ciHash = ciHash,
                name = name,
                birthDate = birthDate,
                gender = gender,
            ),
        )
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

    private fun buildSelfRedirectUrl(
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

    private fun buildGuardianRedirectUrl(minorPublicCode: String): String {
        val params =
            listOf(
                "status" to "success",
                "minorPublicCode" to minorPublicCode,
            ).joinToString("&") { (k, v) ->
                "$k=${URLEncoder.encode(v, StandardCharsets.UTF_8)}"
            }
        return "${guardianProperties.webBaseUrl}/pass/result#$params"
    }

    companion object {
        private const val MOBILE_OK_SUCCESS = "2000"
        private const val ADULT_AGE = 19
    }
}
