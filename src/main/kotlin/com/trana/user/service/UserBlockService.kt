package com.trana.user.service

import com.trana.audit.AuditEvent
import com.trana.audit.AuditLogger
import com.trana.user.UserException
import com.trana.user.dto.BlockUserResponse
import com.trana.user.dto.BlockedUserView
import com.trana.user.entity.UserBlock
import com.trana.user.repository.UserBlockRepository
import com.trana.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 사용자 차단(Block) — App Store UGC 심사 대응.
 *
 * - 단방향(blocker → blocked). 차단당한 사용자가 생성한 계약을 blocker 화면에서 숨김.
 * - 데이터 삭제 없음. 순수 가시성 필터([blockedCreatorIds] 를 계약 도메인이 소비).
 * - 차단당한 사용자에게 알림 없음(silent).
 */
@Service
@Transactional
class UserBlockService(
    private val userBlockRepository: UserBlockRepository,
    private val userRepository: UserRepository,
    private val auditLogger: AuditLogger,
) {
    /** 차단 — 멱등(이미 차단돼 있으면 재저장 안 함). 자기 자신 차단 불가. */
    fun block(
        blockerUserId: Long,
        blockedUserId: Long,
        reason: String? = null,
    ): BlockUserResponse {
        if (blockerUserId == blockedUserId) throw UserException.CannotBlockSelf()
        val blocked =
            userRepository
                .findById(blockedUserId)
                .orElseThrow { UserException.NotFound("userId=$blockedUserId") }
        val shareCode = checkNotNull(blocked.shareCode) { "차단 대상 shareCode 없음 userId=$blockedUserId" }

        if (!userBlockRepository.existsByBlockerUserIdAndBlockedUserId(blockerUserId, blockedUserId)) {
            val saved = userBlockRepository.save(UserBlock(blockerUserId, blockedUserId, reason))
            auditLogger.log(
                eventType = AuditEvent.USER_BLOCKED,
                actorUserId = blockerUserId,
                entityType = ENTITY_USER,
                entityId = blockedUserId,
                metadata = mapOf("reason" to reason),
            )
            return BlockUserResponse(shareCode, saved.createdAt!!)
        }
        val existing =
            userBlockRepository
                .findAllByBlockerUserIdOrderByCreatedAtDesc(blockerUserId)
                .first { it.blockedUserId == blockedUserId }
        return BlockUserResponse(shareCode, existing.createdAt!!)
    }

    /** 차단 해제 — 상대 shareCode 기준. 이미 해제 상태여도 성공(멱등). */
    fun unblock(
        blockerUserId: Long,
        blockedShareCode: String,
    ) {
        val blocked =
            userRepository.findByShareCode(blockedShareCode)
                ?: throw UserException.NotFound("shareCode=$blockedShareCode")
        val blockedId = checkNotNull(blocked.id)
        val removed = userBlockRepository.deleteByBlockerUserIdAndBlockedUserId(blockerUserId, blockedId)
        if (removed > 0) {
            auditLogger.log(
                eventType = AuditEvent.USER_UNBLOCKED,
                actorUserId = blockerUserId,
                entityType = ENTITY_USER,
                entityId = blockedId,
            )
        }
    }

    @Transactional(readOnly = true)
    fun listBlocked(blockerUserId: Long): List<BlockedUserView> {
        val blocks = userBlockRepository.findAllByBlockerUserIdOrderByCreatedAtDesc(blockerUserId)
        if (blocks.isEmpty()) return emptyList()
        val usersById = userRepository.findAllById(blocks.map { it.blockedUserId }).associateBy { it.id }
        return blocks.map { block ->
            val user = usersById[block.blockedUserId]
            BlockedUserView(
                shareCode = user?.shareCode ?: "UNKNOWN",
                blockedAt = block.createdAt!!,
            )
        }
    }

    /** 계약 가시성 필터용 — userId 가 차단한 사용자 id 집합. */
    @Transactional(readOnly = true)
    fun blockedCreatorIds(userId: Long): Set<Long> = userBlockRepository.findBlockedUserIds(userId).toSet()

    companion object {
        private const val ENTITY_USER = "USER"
    }
}
