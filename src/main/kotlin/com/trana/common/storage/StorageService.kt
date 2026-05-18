package com.trana.common.storage

/**
 * 객체 저장소 추상화. AWS S3 / NCP Object Storage / Mock 등 교체 가능.
 *
 * - 도메인에서 직접 S3 SDK 호출 X — 이 인터페이스만 의존
 * - key 패턴은 호출자(도메인)가 결정 (예: identity/{requestId}/id-card.jpg)
 * - 예외는 SDK 예외 그대로 throw (필요 시 추후 StorageException으로 wrapping)
 */
interface StorageService {
    /**
     * 객체 업로드. 동일 key 존재 시 덮어씀.
     *
     * @param contentType MIME 타입 (S3 metadata로 저장, 예: "image/jpeg")
     */
    fun put(
        key: String,
        bytes: ByteArray,
        contentType: String,
    )

    /**
     * 객체 다운로드. 객체 없으면 [software.amazon.awssdk.services.s3.model.NoSuchKeyException] throw.
     */
    fun get(key: String): ByteArray

    /**
     * 객체 삭제. 객체 없어도 성공 (S3 idempotent).
     */
    fun delete(key: String)
}
