package com.trana.identity.service

import com.trana.identity.adapter.pass.PassKeyInfoLoader
import com.trana.identity.adapter.pass.PassProperties
import com.trana.identity.adapter.pass.PassTokenIssuer
import com.trana.identity.dto.MOKReqClientInfoResponse
import com.trana.identity.entity.IdentityVerification
import com.trana.identity.repository.IdentityVerificationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * PASS (mobileOK V3 표준창) 본인확인 가입 흐름 — req-client-info endpoint 의 핵심 로직.
 *
 * 흐름:
 * 1. signupSessionId TTL 검증 (KycStateLookup)
 * 2. PassTokenIssuer 로 clientTxId 발급
 * 3. PENDING IdentityVerification row INSERT (PASS factory, NCP 필드 모두 null)
 * 4. PassTokenIssuer 로 encryptReqClientInfo 생성 (RSA-OAEP)
 * 5. PassKeyInfoLoader / PassProperties 로 MOKReqClientInfo wrapper 조립 → 반환
 *
 * 외부 I/O X — 순수 DB + 메모리 + 암호화. 트랜잭션 안에서 안전.
 */
@Service
@Transactional
class PassSignupService(
    private val stateLookup: KycStateLookup,
    private val tokenIssuer: PassTokenIssuer,
    private val keyInfoLoader: PassKeyInfoLoader,
    private val verificationRepository: IdentityVerificationRepository,
    private val properties: PassProperties,
) {
    fun issueReqClientInfo(signupSessionId: UUID): MOKReqClientInfoResponse {
        stateLookup.validateSignupSession(signupSessionId)

        val clientTxId = tokenIssuer.generateClientTxId()
        verificationRepository.save(
            IdentityVerification.startPassSignup(
                signupSessionId = signupSessionId,
                clientTxId = clientTxId,
            ),
        )

        val encryptReqClientInfo = tokenIssuer.encryptReqClientInfo(clientTxId)

        return MOKReqClientInfoResponse(
            serviceId = keyInfoLoader.get().serviceId,
            encryptReqClientInfo = encryptReqClientInfo,
            serviceType = properties.serviceType,
            usageCode = properties.usageCode,
            retTransferType = RET_TRANSFER_TYPE_MOK_TOKEN,
            returnUrl = properties.returnUrl,
            encryptVersion = ENCRYPT_VERSION_V2,
        )
    }

    companion object {
        private const val RET_TRANSFER_TYPE_MOK_TOKEN = "MOKToken"
        private const val ENCRYPT_VERSION_V2 = "V2"
    }
}
