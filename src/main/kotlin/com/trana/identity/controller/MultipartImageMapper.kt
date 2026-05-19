package com.trana.identity.controller

import com.trana.identity.IdentityException
import com.trana.identity.adapter.ImageFormat
import com.trana.identity.adapter.ImageInput
import org.springframework.web.multipart.MultipartFile

/**
 * MultipartFile → ImageInput 변환 + MIME 검증.
 *
 * - 본인 KYC (IdentityController) + 보호자 KYC (GuardianIdentityController) 공유
 * - image/jpeg, image/png만 허용 — adapter ImageFormat과 일치
 *
 * `internal`: 같은 모듈(production source set) 내에서만 호출 — 외부 노출 X
 */
internal fun MultipartFile.toImageInput(): ImageInput {
    val mime = contentType?.lowercase() ?: throw IdentityException.FileInvalid("Content-Type missing")
    val format =
        when (mime) {
            "image/jpeg", "image/jpg" -> ImageFormat.JPG
            "image/png" -> ImageFormat.PNG
            else -> throw IdentityException.FileInvalid("지원하지 않는 MIME: $mime (image/jpeg, image/png만 허용)")
        }
    return ImageInput(
        bytes = bytes,
        format = format,
        originalFilename = originalFilename ?: "upload.${format.extension}",
    )
}
