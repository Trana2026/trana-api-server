package com.trana.audit

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles

/**
 * WORM 3중 방어 통합 테스트.
 *
 * - DB 트리거(worm_protect): raw SQL UPDATE/DELETE 차단 검증
 * - JPA @Immutable: Kotlin val로 정적 차단 (컴파일 레벨, 런타임 검증 불필요)
 * - DB role 권한: 별도 작업 (dev/prod만, 추후 검증)
 *
 * 추가로 JSONB/INET 매핑 왕복도 검증.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuditLogWormTest
    @Autowired
    constructor(
        private val repository: AuditLogRepository,
        private val jdbcTemplate: JdbcTemplate,
    ) {
        @BeforeEach
        fun clean() {
            // TRUNCATE는 BEFORE ROW 트리거가 안 잡음 — 정리 가능
            // 운영에서 막는 건 Step 5(role 권한)
            jdbcTemplate.execute("TRUNCATE audit_logs")
        }

        @Test
        fun insertSucceeds() {
            repository.save(AuditLog(eventType = "TEST_INSERT", actorUserId = 1L))

            val all = repository.findAll()
            Assertions.assertEquals(1, all.size)
            Assertions.assertEquals("TEST_INSERT", all[0].eventType)
            Assertions.assertEquals(1L, all[0].actorUserId)
            Assertions.assertNotNull(all[0].id)
            Assertions.assertNotNull(all[0].createdAt)
        }

        @Test
        fun updateIsRejectedByWormTrigger() {
            repository.save(AuditLog(eventType = "BEFORE_UPDATE", actorUserId = 1L))

            Assertions.assertThrows(DataAccessException::class.java) {
                jdbcTemplate.update(
                    "UPDATE audit_logs SET event_type = 'HACK' WHERE event_type = 'BEFORE_UPDATE'",
                )
            }
        }

        @Test
        fun deleteIsRejectedByWormTrigger() {
            repository.save(AuditLog(eventType = "BEFORE_DELETE", actorUserId = 1L))

            Assertions.assertThrows(DataAccessException::class.java) {
                jdbcTemplate.update("DELETE FROM audit_logs WHERE event_type = 'BEFORE_DELETE'")
            }
        }

        @Test
        fun jsonbMetadataRoundTrip() {
            val meta = mapOf("provider" to "KAKAO", "ip_country" to "KR", "score" to 95)
            repository.save(AuditLog(eventType = "JSONB_TEST", metadata = meta))

            val saved = repository.findAll().first()
            Assertions.assertEquals("KAKAO", saved.metadata?.get("provider"))
            Assertions.assertEquals("KR", saved.metadata?.get("ip_country"))
            // JSONB 숫자는 Long 또는 Integer로 역직렬화될 수 있음
            Assertions.assertEquals(95, (saved.metadata?.get("score") as Number).toInt())
        }

        @Test
        fun inetIpRoundTrip() {
            repository.save(AuditLog(eventType = "INET_TEST", ip = "192.168.0.1"))

            val saved = repository.findAll().first()
            Assertions.assertEquals("192.168.0.1", saved.ip)
        }
    }
