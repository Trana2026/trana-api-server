package com.trana.terms.repository

import com.trana.terms.entity.ConsentContextType
import com.trana.terms.entity.UserConsent
import com.trana.user.entity.AgeGroup
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserConsentRepositoryTest
    @Autowired
    constructor(
        private val userConsentRepository: UserConsentRepository,
    ) {
        @PersistenceContext
        private lateinit var entityManager: EntityManager

        @Nested
        inner class GuardianLinkUniqueIndex {
            @Test
            fun rejectsDuplicateTokenAndTermsVersion() {
                val token = "TST-EE-001-AAAAAAAAA"
                val termsVersionId = 1L // seed terms (V4)
                userConsentRepository.save(
                    UserConsent(
                        termsVersionId = termsVersionId,
                        contextType = ConsentContextType.GUARDIAN_CONSENT,
                        ageGroup = AgeGroup.MINOR,
                        ip = "127.0.0.1",
                        guardianLinkToken = token,
                    ),
                )
                entityManager.flush()

                // 같은 (token, termsVersionId) 재 INSERT → DB UNIQUE INDEX 차단
                Assertions.assertThrows(DataIntegrityViolationException::class.java) {
                    userConsentRepository.save(
                        UserConsent(
                            termsVersionId = termsVersionId,
                            contextType = ConsentContextType.GUARDIAN_CONSENT,
                            ageGroup = AgeGroup.MINOR,
                            ip = "127.0.0.1",
                            guardianLinkToken = token,
                        ),
                    )
                    entityManager.flush()
                }
            }
        }
    }
