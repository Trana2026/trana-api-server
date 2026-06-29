package com.trana.identity.service

import com.trana.guardian.service.GuardianLinkService
import com.trana.identity.adapter.pass.PassKeyInfoLoader
import com.trana.identity.adapter.pass.PassProperties
import com.trana.identity.adapter.pass.PassTokenIssuer
import com.trana.identity.dto.MOKReqClientInfoResponse
import com.trana.identity.entity.IdentityVerification
import com.trana.identity.repository.IdentityVerificationRepository
import com.trana.user.entity.AgeGroup
import com.trana.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 보호자 PASS 표준창 req-client-info — guardianLinkToken 기반 진입.
 *
 * 흐름:
 * 1. guardianLinkToken 활성 검증 (TTL 3일, 미사용)
 * 2. subjectUserId(자녀) MINOR + 보호자 미인증 검증
 * 3. clientTxId 발급
 * 4. PENDING IdentityVerification (purpose=GUARDIAN, subjectUserId, guardianLinkToken) INSERT
 * 5. MOKReqClientInfo 응답 조립 (returnUrl 은 통합 endpoint — purpose 분기는 return 에서)
 */
@Service
@Transactional
class PassGuardianSignupService(
    private val guardianLinkService: GuardianLinkService,
    private val userService: UserService,
    private val tokenIssuer: PassTokenIssuer,
    private val keyInfoLoader: PassKeyInfoLoader,
    private val verificationRepository: IdentityVerificationRepository,
    private val properties: PassProperties,
) {
    fun issueReqClientInfo(guardianLinkToken: String): MOKReqClientInfoResponse {
        val link = guardianLinkService.findActive(guardianLinkToken)
        val subject = userService.getById(link.userId)
        check(subject.ageGroup == AgeGroup.MINOR) { "보호자 PASS 는 미성년자 대상만" }
        check(subject.guardianVerifiedAt == null) { "이미 보호자 인증 완료된 미성년자" }

        val clientTxId = tokenIssuer.generateClientTxId()
        verificationRepository.save(
            IdentityVerification.startPassGuardian(
                subjectUserId = link.userId,
                guardianLinkToken = guardianLinkToken,
                clientTxId = clientTxId,
            ),
        )

        val encrypted = tokenIssuer.encryptReqClientInfo(clientTxId)
        return MOKReqClientInfoResponse(
            serviceId = keyInfoLoader.get().serviceId,
            encryptReqClientInfo = encrypted,
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
