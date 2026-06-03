package com.trana.guardian.repository

import com.trana.guardian.entity.GuardianLink
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.time.Instant

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class GuardianLinkRepositoryTest
    @Autowired
    constructor(
        private val guardianLinkRepository: GuardianLinkRepository,
    ) {
        @PersistenceContext
        private lateinit var entityManager: EntityManager

        @Nested
        inner class MarkUsedAtomically {
            @Test
            fun returnsOneAndSetsUsedAtWhenActive() {
                val now = Instant.now()
                val token = "TST-CC-001-AAAAAAAAA"
                guardianLinkRepository.save(
                    GuardianLink(
                        token = token,
                        userId = 999_801L,
                        expiresAt = now.plus(Duration.ofDays(3)),
                    ),
                )

                val affected = guardianLinkRepository.markUsedAtomically(token, now)
                entityManager.flush()
                entityManager.clear()

                Assertions.assertEquals(1, affected)
                val reloaded = guardianLinkRepository.findById(token).orElseThrow()
                Assertions.assertNotNull(reloaded.usedAt)
            }

            @Test
            fun returnsZeroWhenAlreadyUsed() {
                val now = Instant.now()
                val token = "TST-CC-002-AAAAAAAAA"
                guardianLinkRepository.save(
                    GuardianLink(
                        token = token,
                        userId = 999_802L,
                        expiresAt = now.plus(Duration.ofDays(3)),
                    ),
                )
                // 첫 호출: 정상 사용
                guardianLinkRepository.markUsedAtomically(token, now)
                entityManager.flush()
                entityManager.clear()
                val firstUsedAt = guardianLinkRepository.findById(token).orElseThrow().usedAt

                // 두 번째 호출: 이미 사용된 token → affected=0
                val affected = guardianLinkRepository.markUsedAtomically(token, Instant.now())
                entityManager.flush()
                entityManager.clear()

                Assertions.assertEquals(0, affected)
                val reloaded = guardianLinkRepository.findById(token).orElseThrow()
                Assertions.assertEquals(firstUsedAt, reloaded.usedAt) // usedAt 보존
            }

            @Test
            fun returnsZeroWhenExpired() {
                val now = Instant.now()
                val token = "TST-CC-003-AAAAAAAAA"
                guardianLinkRepository.save(
                    GuardianLink(
                        token = token,
                        userId = 999_803L,
                        expiresAt = now.minus(Duration.ofMinutes(1)), // 이미 만료
                    ),
                )

                val affected = guardianLinkRepository.markUsedAtomically(token, now)
                entityManager.flush()
                entityManager.clear()

                Assertions.assertEquals(0, affected)
                val reloaded = guardianLinkRepository.findById(token).orElseThrow()
                Assertions.assertNull(reloaded.usedAt)
            }

            @Test
            fun returnsZeroWhenTokenNotFound() {
                val affected =
                    guardianLinkRepository.markUsedAtomically(
                        token = "TST-CC-004-NOT-EXIST",
                        now = Instant.now(),
                    )

                Assertions.assertEquals(0, affected)
            }
        }
    }
