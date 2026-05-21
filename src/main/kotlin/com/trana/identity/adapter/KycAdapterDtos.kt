package com.trana.identity.adapter

import java.time.LocalDate

// ───── input ─────
@Suppress("ArrayInDataClass")
data class ImageInput(
    val bytes: ByteArray,
    val format: ImageFormat, // JPG | PNG (NCP는 PDF/TIFF도 받지만 우리는 신분증/셀카만)
    val originalFilename: String, // multipart 전송 시 name 필드
)

enum class ImageFormat(
    val mime: String,
    val extension: String,
) {
    JPG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    ;

    companion object {
        fun fromMime(mime: String): ImageFormat =
            entries.firstOrNull { it.mime.equals(mime, ignoreCase = true) }
                ?: error("Unsupported MIME: $mime")
    }
}

enum class IdType { ID_CARD, DRIVER_LICENSE, ALIEN_REGISTRATION }

enum class Gender { MALE, FEMALE }

// ───── output: 신분증 OCR (3종 분리) ─────
sealed interface IdCardRecognitionResult {
    val name: String
    val birthDate: LocalDate
    val gender: Gender
    val issueDate: LocalDate?
    val rawConfidence: Boolean // NCP isConfident (참고용 - 행안부 진위확인 아님)
    val faceImageBase64: String? // NCP가 잘라준 alignedImage (Face Compare 호출 시 input으로)

    /** 주민등록증 */
    data class ResidentIdCard(
        override val name: String,
        override val birthDate: LocalDate,
        override val gender: Gender,
        override val issueDate: LocalDate?,
        override val rawConfidence: Boolean,
        override val faceImageBase64: String?,
        val personalNumberHash: String, // SHA-256 (평문 저장 X)
        val address: String?,
    ) : IdCardRecognitionResult

    /** 운전면허증 */
    data class DriverLicense(
        override val name: String,
        override val birthDate: LocalDate,
        override val gender: Gender,
        override val issueDate: LocalDate?,
        override val rawConfidence: Boolean,
        override val faceImageBase64: String?,
        val personalNumberHash: String, // 운전면허증도 주민번호 노출
        val licenseNumber: String, // 면허번호 (12자리)
        val address: String?,
    ) : IdCardRecognitionResult

    /** 외국인등록증 */
    data class AlienRegistration(
        override val name: String,
        override val birthDate: LocalDate,
        override val gender: Gender,
        override val issueDate: LocalDate?,
        override val rawConfidence: Boolean,
        override val faceImageBase64: String?,
        val alienRegNumberHash: String, // 외국인등록번호 SHA-256 (주민번호와 구조 동일 13자리)
        val nationality: String,
        val visaType: String?,
    ) : IdCardRecognitionResult
}

/** sealed sub class → IdType enum 도출 (DB 저장/외부 노출용) */
val IdCardRecognitionResult.idType: IdType
    get() =
        when (this) {
            is IdCardRecognitionResult.ResidentIdCard -> IdType.ID_CARD
            is IdCardRecognitionResult.DriverLicense -> IdType.DRIVER_LICENSE
            is IdCardRecognitionResult.AlienRegistration -> IdType.ALIEN_REGISTRATION
        }

// ───── output wrapper: 외부용 result + 내부용 평문 분리 ─────
data class IdCardOcrOutput(
    val result: IdCardRecognitionResult, // 외부 노출용 (hash만, 기존)
    val sensitive: IdCardSensitiveData, // 내부 전용 (Service가 세션 DB에 암호화 저장)
)

/**
 * identifierHash 도출용 raw value.
 * 모든 신분증은 adapter가 이미 SHA-256 hashed 반환.
 */

val IdCardRecognitionResult.identifierHashRaw: String
    get() =
        when (this) {
            is IdCardRecognitionResult.ResidentIdCard -> personalNumberHash
            is IdCardRecognitionResult.DriverLicense -> personalNumberHash
            is IdCardRecognitionResult.AlienRegistration -> alienRegNumberHash
        }

/**
 * adapter Gender → domain user Gender.
 * KYC에서 OTHER 케이스는 발생하지 않음 (adapter Gender enum이 MALE/FEMALE만).
 */
fun Gender.toDomainGender(): com.trana.user.entity.Gender =
    when (this) {
        Gender.MALE -> com.trana.user.entity.Gender.MALE
        Gender.FEMALE -> com.trana.user.entity.Gender.FEMALE
    }

data class MaskPolygon(
    val vertices: List<MaskVertex>,
)

data class MaskVertex(
    val x: Double,
    val y: Double,
)

/**
 * NCP Verify API 호출에 필요한 평문 식별 정보.
 *
 * 어댑터 → Service까지의 메모리 전달용. **응답 DTO에는 절대 노출 X**.
 * Service가 즉시 BytesEncryptor로 암호화하여 id_card_verify_session 테이블에 저장.
 */
@Suppress("LongParameterList")
data class IdCardSensitiveData(
    val requestId: String, // NCP Document API requestId (10분 유효, Verify 호출 키)
    val name: String,
    val personalNumber: String? = null, // ic/dl/ac 13자리 (외국인등록번호 포함)
    val licenseNumber: String? = null, // dl 면허번호
    val licenseSecurityCode: String? = null, // dl 암호일련번호 (옵션 — skipCodeCheck 가능)
    val serialNumber: String? = null, // ac Verify 필수
    val issueDate: LocalDate? = null, // ic/dl/ac 모두 (Verify 필수)
    val maskRegions: List<MaskPolygon> = emptyList(),
)

// ───── output: 얼굴비교 ─────
data class FaceCompareResult(
    val similarity: Double, // 0.0 ~ 1.0
    val isMatch: Boolean, // 도메인 threshold 적용 결과 (예: 0.8 이상)
)

// ───── input: 신분증 진위확인 ─────
@Suppress("LongParameterList")
data class IdCardVerifyInput(
    val requestId: String, // NCP Document API의 requestId (10분 유효)
    val idType: IdType, // 분기 키 (ic/dl/ac)
    val name: String,
    val personalNum: String? = null, // ic/dl 주민번호 13자리 (sanitized)
    val licenseNum: String? = null, // dl 면허번호
    val licenseCode: String? = null, // dl 암호일련번호 (옵션 — null이면 skipCodeCheck)
    val serialNum: String? = null, // ac 시리얼 (Verify 필수)
    val alienRegNum: String? = null, // ac 외국인등록번호 13자리
    val issueDate: LocalDate? = null, // 모든 타입 Verify 필수
)

// ───── output: 신분증 진위확인 ─────
data class IdCardVerifyResult(
    val isValid: Boolean, // NCP result == "SUCCESS"
    val errorCode: String? = null, // NCP code (실패 시)
    val errorMessage: String? = null, // NCP message
)
