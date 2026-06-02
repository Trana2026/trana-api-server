package com.trana.identity.repository

import com.trana.identity.entity.IdentityVerification
import com.trana.user.entity.AgeGroup
import com.trana.user.entity.Gender
import com.trana.user.entity.User
import com.trana.user.entity.UserStatus
import com.trana.user.repository.UserRepository
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class IdentityVerificationRepositoryTest
    @Autowired
    constructor(
        private val identityVerificationRepository: IdentityVerificationRepository,
        private val userRepository: UserRepository,
    ) {
        @Nested
        inner class ExistsActiveSuccessByIdentifierHash {
            @Test
            fun returnsTrueForActiveUserWithSuccessVerification() {
                val user =
                    User(
                        publicCode = "TST-IDV-001",
                        ageGroup = AgeGroup.ADULT,
                        status = UserStatus.ACTIVE,
                        name = "홍길동",
                        birthDate = "1987-01-01",
                        gender = Gender.MALE,
                        phone = "010-0000-0000",
                    )
                val savedUser = userRepository.save(user)

                val verification =
                    IdentityVerification.startSignup(
                        idType = "RRN",
                        ncpDocumentRequestId = "ncp-req-active-001",
                        identifierHash = "hash-active-001",
                        signupSessionId = UUID.randomUUID(),
                        name = "홍길동",
                        birthDate = LocalDate.of(1987, 1, 1),
                        gender = Gender.MALE,
                    )
                verification.markVerifyPassed()
                verification.markCompareSuccess(similarity = 0.95, boundUserId = savedUser.id!!)
                identityVerificationRepository.save(verification)

                val result = identityVerificationRepository.existsActiveSuccessByIdentifierHash("hash-active-001")

                Assertions.assertTrue(result)
            }

            @Test
            fun returnsFalseForWithdrawnUserWithSuccessVerification() {
                val user =
                    User(
                        publicCode = "TST-IDV-002",
                        ageGroup = AgeGroup.ADULT,
                        status = UserStatus.ACTIVE,
                        name = "김탈퇴",
                        birthDate = "1987-02-02",
                        gender = Gender.FEMALE,
                        phone = "010-1111-1111",
                    )
                val savedUser = userRepository.save(user)
                savedUser.withdraw()
                userRepository.save(savedUser)

                val verification =
                    IdentityVerification.startSignup(
                        idType = "RRN",
                        ncpDocumentRequestId = "ncp-req-withdrawn-001",
                        identifierHash = "hash-withdrawn-001",
                        signupSessionId = UUID.randomUUID(),
                        name = "김탈퇴",
                        birthDate = LocalDate.of(1987, 2, 2),
                        gender = Gender.FEMALE,
                    )
                verification.markVerifyPassed()
                verification.markCompareSuccess(similarity = 0.95, boundUserId = savedUser.id!!)
                identityVerificationRepository.save(verification)

                val result = identityVerificationRepository.existsActiveSuccessByIdentifierHash("hash-withdrawn-001")

                Assertions.assertFalse(result)
            }

            @Test
            fun returnsFalseForFailedVerification() {
                val verification =
                    IdentityVerification.startSignup(
                        idType = "RRN",
                        ncpDocumentRequestId = "ncp-req-failed-001",
                        identifierHash = "hash-failed-001",
                        signupSessionId = UUID.randomUUID(),
                        name = "박실패",
                        birthDate = LocalDate.of(1990, 3, 3),
                        gender = Gender.MALE,
                    )
                verification.markVerifyFailed(errorCode = "VERIFY_ERR", errorMessage = "verify failed")
                identityVerificationRepository.save(verification)

                val result = identityVerificationRepository.existsActiveSuccessByIdentifierHash("hash-failed-001")

                Assertions.assertFalse(result)
            }
        }
    }
