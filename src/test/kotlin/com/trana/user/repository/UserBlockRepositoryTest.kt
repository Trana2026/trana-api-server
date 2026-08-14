package com.trana.user.repository

import com.trana.user.entity.UserBlock
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserBlockRepositoryTest
    @org.springframework.beans.factory.annotation.Autowired
    constructor(
        private val userBlockRepository: UserBlockRepository,
    ) {
        @Test
        fun findBlockedUserIdsReturnsBlockedTargets() {
            userBlockRepository.save(UserBlock(blockerUserId = 900_001L, blockedUserId = 900_002L))
            userBlockRepository.save(UserBlock(blockerUserId = 900_001L, blockedUserId = 900_003L))
            userBlockRepository.save(UserBlock(blockerUserId = 900_009L, blockedUserId = 900_002L))

            val blocked = userBlockRepository.findBlockedUserIds(900_001L)

            Assertions.assertEquals(setOf(900_002L, 900_003L), blocked.toSet())
        }

        @Test
        fun existsAndDeleteByPairWork() {
            userBlockRepository.save(UserBlock(blockerUserId = 910_001L, blockedUserId = 910_002L))

            Assertions.assertTrue(
                userBlockRepository.existsByBlockerUserIdAndBlockedUserId(910_001L, 910_002L),
            )
            val removed = userBlockRepository.deleteByBlockerUserIdAndBlockedUserId(910_001L, 910_002L)
            Assertions.assertEquals(1L, removed)
            Assertions.assertFalse(
                userBlockRepository.existsByBlockerUserIdAndBlockedUserId(910_001L, 910_002L),
            )
        }

        @Test
        fun rejectsSelfBlockViaCheckConstraint() {
            Assertions.assertThrows(DataIntegrityViolationException::class.java) {
                userBlockRepository.saveAndFlush(UserBlock(blockerUserId = 920_001L, blockedUserId = 920_001L))
            }
        }
    }
