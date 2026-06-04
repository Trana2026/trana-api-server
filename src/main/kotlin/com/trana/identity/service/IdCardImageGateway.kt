package com.trana.identity.service

import com.trana.common.storage.StorageService
import com.trana.identity.adapter.ImageFormat
import com.trana.identity.adapter.ImageInput
import com.trana.identity.entity.IdCardVerifySession
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 신분증 이미지 S3 게이트웨이 (refactor q).
 *
 * - load: 세션의 idCardS3Key / idCardMime → S3 fetch → ImageInput
 * - deleteSwallow: 실패 시 lifecycle 1일 정리 의존 (idempotent — swallow + warn)
 *
 * 이전: KycSessionService / KycSignupService / KycGuardianService / IdentitySessionPurger 의 private 메서드 4중 중복
 * 정리: 단일 진입점 → mime 검증 강화 / S3 prefix 변경 등 정책 변화 시 한 곳만 수정
 */
@Component
class IdCardImageGateway(
    private val storageService: StorageService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun load(session: IdCardVerifySession): ImageInput {
        val s3Key = checkNotNull(session.idCardS3Key) { "session.idCardS3Key null" }
        val mime = checkNotNull(session.idCardMime) { "session.idCardMime null" }
        val format = ImageFormat.fromMime(mime)
        return ImageInput(
            bytes = storageService.get(s3Key),
            format = format,
            originalFilename = "id-card.${format.extension}",
        )
    }

    /** S3 객체 삭제 — 실패 시 swallow + warn (lifecycle 1d cleanup 가 fallback). */
    fun deleteSwallow(s3Key: String?) {
        if (s3Key == null) return
        runCatching { storageService.delete(s3Key) }
            .onFailure { log.warn("S3 id-card delete failed (lifecycle 1d will cleanup): key={}", s3Key, it) }
    }
}
