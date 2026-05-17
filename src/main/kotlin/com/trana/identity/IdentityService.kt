package com.trana.identity

import com.trana.identity.adapter.FaceCompareAdapter
import com.trana.identity.adapter.FaceCompareResult
import com.trana.identity.adapter.IdCardOcrAdapter
import com.trana.identity.adapter.IdCardOcrOutput
import com.trana.identity.adapter.IdCardSensitiveData
import com.trana.identity.adapter.IdCardVerifyAdapter
import com.trana.identity.adapter.IdCardVerifyInput
import com.trana.identity.adapter.IdCardVerifyResult
import com.trana.identity.adapter.IdType
import com.trana.identity.adapter.ImageInput
import com.trana.identity.adapter.idType
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

/**
 * KYC 도메인 서비스.
 *
 * - 어댑터(OCR/Verify/Compare) 오케스트레이션
 * - OCR 단계: 평문 식별번호를 세션 DB에 암호화 저장 (Verify 단계용)
 * - Verify 단계: 세션에서 평문 조회 → idType별 input 변환 → 어댑터 호출
 * - Compare 단계: 단순 위임
 *
 * (추후) IdentityVerification Entity에 영구 결과 기록 + User 백필 + audit log
 */
@Service
class IdentityService(
    private val idCardOcrAdapter: IdCardOcrAdapter,
    private val idCardVerifyAdapter: IdCardVerifyAdapter,
    private val faceCompareAdapter: FaceCompareAdapter,
    private val sessionService: IdCardVerifySessionService,
) {
    fun recognizeIdCard(image: ImageInput): IdCardOcrOutput {
        val output = idCardOcrAdapter.recognizeIdCard(image)
        sessionService.save(output.sensitive.toSessionData(output.result.idType))
        return output
    }

    fun verifyIdCard(requestId: String): IdCardVerifyResult {
        val session = sessionService.findActive(requestId)
        return idCardVerifyAdapter.verify(session.toVerifyInput())
    }

    fun compareFaces(
        idCardImage: ImageInput,
        selfieImage: ImageInput,
    ): FaceCompareResult = faceCompareAdapter.compareFaces(idCardImage, selfieImage)

    private fun IdCardSensitiveData.toSessionData(idType: IdType): IdCardSessionData =
        IdCardSessionData(
            requestId = requestId,
            idType = idType,
            name = name,
            personalNumber = personalNumber,
            licenseNumber = licenseNumber,
            licenseSecurityCode = licenseSecurityCode,
            passportNumber = passportNumber,
            birthDate = birthDate,
            serialNumber = serialNumber,
            issueDate = issueDate,
            expireDate = expireDate,
            expiresAt = OffsetDateTime.now().plusMinutes(SESSION_TTL_MINUTES),
        )

    private fun IdCardSessionData.toVerifyInput(): IdCardVerifyInput {
        val base =
            IdCardVerifyInput(
                requestId = requestId,
                idType = idType,
                name = name,
                issueDate = issueDate,
            )
        return when (idType) {
            IdType.ID_CARD -> {
                base.copy(personalNum = personalNumber)
            }

            IdType.DRIVER_LICENSE -> {
                base.copy(
                    personalNum = personalNumber,
                    licenseNum = licenseNumber,
                    licenseCode = licenseSecurityCode,
                )
            }

            IdType.PASSPORT -> {
                base.copy(
                    passportNum = passportNumber,
                    birthDate = birthDate,
                    expireDate = expireDate,
                )
            }

            IdType.ALIEN_REGISTRATION -> {
                base.copy(
                    alienRegNum = personalNumber,
                    serialNum = serialNumber,
                )
            }
        }
    }
}

private const val SESSION_TTL_MINUTES = 10L
