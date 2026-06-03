package com.trana.contract.service

import com.trana.contract.ContractException
import com.trana.contract.entity.ConsentType
import com.trana.contract.entity.Contract
import com.trana.contract.entity.ContractStatus
import com.trana.contract.repository.ContractRepository
import com.trana.guardian.entity.GuardianLink
import com.trana.guardian.entity.LinkPurpose
import com.trana.guardian.service.GuardianLinkService
import com.trana.identity.entity.VerificationPurpose
import com.trana.identity.entity.VerificationStatus
import com.trana.identity.repository.IdentityVerificationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 미성년자 계약 보호자 동의 흐름.
 *
 * 흐름:
 * 1. requestConsent(publicCode, minorUserId)
 *    - 미성년자가 본인 계약(GUARDIAN_REQUIRED + DRAFT + 미동의)에 대해 호출
 *    - GuardianLinkService.createForContract 위임 → 토큰 발급
 *    - 응답 토큰을 보호자에게 공유 (URL 변환은 Controller 가 web base URL 결합)
 *
 * 2. approveConsent(token)
 *    - 보호자가 web 단순 동의 (token URL 클릭 + 약관 동의 클릭) 시 호출
 *    - 토큰 검증 (active + purpose=CONTRACT_CONSENT)
 *    - 미성년 user 의 가입 단계 보호자 ID 자동 매핑 (identity_verifications.purpose=GUARDIAN + SUCCESS)
 *    - contract.markGuardianConsented(guardianId) + link.markUsed()
 *    - 매번 full eKYC 안 함 — 가입 단계 보호자 KYC 1회로 신원 검증 끝
 *
 * 트랜잭션:
 * - approveConsent 는 contract / link 양쪽 변경 → @Transactional 안에서 일관성 보장
 *
 * TODO (W4+):
 * - approve 시 audit (CONTRACT_GUARDIAN_CONSENT_APPROVED)
 * - 재발급 시 기존 active link 강제 만료 처리 (현재는 TTL 자연 만료 의존)
 */
@Service
@Transactional
class ContractGuardianConsentService(
    private val contractRepository: ContractRepository,
    private val guardianLinkService: GuardianLinkService,
    private val identityVerificationRepository: IdentityVerificationRepository,
    private val accessGuard: ContractAccessGuard,
) {
    fun requestConsent(
        publicCode: String,
        minorUserId: Long,
    ): GuardianLink {
        val contract = accessGuard.loadOwnedConsentRequired(publicCode, minorUserId)
        if (contract.status != ContractStatus.IN_PROGRESS && contract.status != ContractStatus.DRAFT) {
            throw ContractException.NotDraft(publicCode, contract.status.name)
        }
        if (contract.guardianConsentAt != null) {
            throw ContractException.GuardianConsentAlready(publicCode)
        }
        return guardianLinkService.createForContract(
            minorUserId = minorUserId,
            contractId = contract.id!!,
        )
    }

    @Suppress("ThrowsCount")
    fun approveConsent(token: String): Contract {
        val link = guardianLinkService.findActive(token)
        if (link.purpose != LinkPurpose.CONTRACT_CONSENT) {
            throw ContractException.InvalidConsentType(
                "계약 보호자 동의용 토큰이 아닙니다 (purpose=${link.purpose})",
            )
        }
        val contractId =
            link.contractId
                ?: throw IllegalStateException(
                    "CONTRACT_CONSENT 토큰에 contractId 가 비어있습니다 (token=${token.take(8)})",
                )

        val contract =
            contractRepository.findById(contractId).orElseThrow {
                IllegalStateException("link.contractId 가 존재하지 않습니다 (contractId=$contractId)")
            }
        if (contract.deletedAt != null) {
            throw ContractException.AlreadyDeleted(contract.publicCode)
        }
        if (contract.consentType != ConsentType.GUARDIAN_REQUIRED) {
            throw ContractException.InvalidConsentType(
                "보호자 동의가 불필요한 계약 (consentType=${contract.consentType})",
            )
        }
        if (contract.guardianConsentAt != null) {
            throw ContractException.GuardianConsentAlready(contract.publicCode)
        }

        val guardianId = resolveGuardianId(minorUserId = link.userId, publicCode = contract.publicCode)
        contract.markGuardianConsented(guardianId)
        guardianLinkService.markUsed(token)
        return contract
    }

    private fun resolveGuardianId(
        minorUserId: Long,
        publicCode: String,
    ): Long {
        val guardianVerification =
            identityVerificationRepository.findFirstBySubjectUserIdAndPurposeAndStatus(
                subjectUserId = minorUserId,
                purpose = VerificationPurpose.GUARDIAN,
                status = VerificationStatus.SUCCESS,
            ) ?: throw ContractException.GuardianConsentRequired(publicCode)
        return guardianVerification.guardianId
            ?: throw IllegalStateException(
                "가입 단계 보호자 verification 에 guardianId 가 없음 (verificationId=${guardianVerification.id})",
            )
    }
}
