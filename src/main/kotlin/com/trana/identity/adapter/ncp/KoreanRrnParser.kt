package com.trana.identity.adapter.ncp

import com.trana.common.crypto.Sha256Hasher
import com.trana.identity.adapter.Gender
import java.time.LocalDate

object KoreanRrnParser {
    data class Parsed(
        val birthDate: LocalDate,
        val gender: Gender,
        val hash: String,
    )

    fun parse(rrn: String): Parsed {
        val sanitized = rrn.replace(Regex("\\D"), "")
        require(sanitized.length == RRN_LENGTH && sanitized.all { it.isDigit() }) {
            "잘못된 주민번호 형식"
        }
        val yy = sanitized.substring(0, 2).toInt()
        val mm = sanitized.substring(2, 4).toInt()
        val dd = sanitized.substring(4, 6).toInt()
        val genderDigit = sanitized[GENDER_DIGIT_INDEX].digitToInt()

        val century =
            when (genderDigit) {
                in CENTURY_1900_CODES -> CENTURY_1900
                in CENTURY_2000_CODES -> CENTURY_2000
                in CENTURY_1800_CODES -> CENTURY_1800
                else -> throw IllegalArgumentException("잘못된 성별 코드")
            }
        val gender = if (genderDigit % 2 == 1) Gender.MALE else Gender.FEMALE

        return Parsed(
            birthDate = LocalDate.of(century + yy, mm, dd),
            gender = gender,
            hash = Sha256Hasher.hashHex(sanitized),
        )
    }

    private const val RRN_LENGTH = 13
    private const val GENDER_DIGIT_INDEX = 6

    private const val CENTURY_1900 = 1900
    private const val CENTURY_2000 = 2000
    private const val CENTURY_1800 = 1800

    // 성별 코드 → 세기 매핑 (주민등록법 시행규칙 기준)
    // 1·2·5·6: 1900년대 (5·6은 외국인) / 3·4·7·8: 2000년대 (7·8은 외국인) / 9·0: 1800년대
    private val CENTURY_1900_CODES = setOf(1, 2, 5, 6)
    private val CENTURY_2000_CODES = setOf(3, 4, 7, 8)
    private val CENTURY_1800_CODES = setOf(9, 0)
}
