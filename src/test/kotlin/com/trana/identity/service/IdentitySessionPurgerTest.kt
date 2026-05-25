package com.trana.identity.service

import com.trana.common.storage.StorageService
import com.trana.identity.entity.IdCardVerifySession
import com.trana.identity.entity.VerificationStatus
import com.trana.identity.repository.IdCardVerifySessionRepository
import com.trana.identity.repository.IdentityVerificationRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional

class IdentitySessionPurgerTest {
    private val sessionRepository: IdCardVerifySessionRepository = mockk()
    private val verificationRepository: IdentityVerificationRepository = mockk()
    private val storageService: StorageService = mockk()

    private val purger =
        IdentitySessionPurger(
            sessionRepository = sessionRepository,
            verificationRepository = verificationRepository,
            storageService = storageService,
        )

    @Test
    fun purgeDeletesS3AndVerificationAndSessionInOrder() {
        val session = session(requestId = "req-1", s3Key = "identity/req-1/id-card.jpg")
        every { sessionRepository.findById("req-1") } returns Optional.of(session)
        every { storageService.delete("identity/req-1/id-card.jpg") } just Runs
        every {
            verificationRepository.deleteByNcpDocumentRequestIdAndStatus("req-1", VerificationStatus.PENDING)
        } returns 1
        every { sessionRepository.deleteById("req-1") } just Runs

        purger.purgeByRequestId("req-1")

        verifyOrder {
            storageService.delete("identity/req-1/id-card.jpg")
            verificationRepository.deleteByNcpDocumentRequestIdAndStatus("req-1", VerificationStatus.PENDING)
            sessionRepository.deleteById("req-1")
        }
    }

    @Test
    fun purgeSkipsS3WhenSessionS3KeyIsNull() {
        val session = session(requestId = "req-1", s3Key = null)
        every { sessionRepository.findById("req-1") } returns Optional.of(session)
        every {
            verificationRepository.deleteByNcpDocumentRequestIdAndStatus("req-1", VerificationStatus.PENDING)
        } returns 0
        every { sessionRepository.deleteById("req-1") } just Runs

        purger.purgeByRequestId("req-1")

        verify(exactly = 0) { storageService.delete(any()) }
        verify { sessionRepository.deleteById("req-1") }
    }

    @Test
    fun purgeContinuesWhenS3DeleteFails() {
        val session = session(requestId = "req-1", s3Key = "identity/req-1/id-card.jpg")
        every { sessionRepository.findById("req-1") } returns Optional.of(session)
        every { storageService.delete(any()) } throws RuntimeException("S3 unreachable")
        every {
            verificationRepository.deleteByNcpDocumentRequestIdAndStatus("req-1", VerificationStatus.PENDING)
        } returns 1
        every { sessionRepository.deleteById("req-1") } just Runs

        purger.purgeByRequestId("req-1")

        verify {
            verificationRepository.deleteByNcpDocumentRequestIdAndStatus("req-1", VerificationStatus.PENDING)
        }
        verify { sessionRepository.deleteById("req-1") }
    }

    @Test
    fun purgeDeletesOnlyVerificationWhenSessionMissing() {
        every { sessionRepository.findById("req-1") } returns Optional.empty()
        every {
            verificationRepository.deleteByNcpDocumentRequestIdAndStatus("req-1", VerificationStatus.PENDING)
        } returns 1

        purger.purgeByRequestId("req-1")

        verify(exactly = 0) { storageService.delete(any()) }
        verify(exactly = 0) { sessionRepository.deleteById(any<String>()) }
        verify {
            verificationRepository.deleteByNcpDocumentRequestIdAndStatus("req-1", VerificationStatus.PENDING)
        }
    }

    private fun session(
        requestId: String,
        s3Key: String?,
    ): IdCardVerifySession =
        IdCardVerifySession(
            requestId = requestId,
            idType = "RESIDENT",
            name = "테스트",
            idCardS3Key = s3Key,
            idCardMime = if (s3Key != null) "image/jpeg" else null,
            expiresAt = Instant.now().minusSeconds(60),
        )
}
