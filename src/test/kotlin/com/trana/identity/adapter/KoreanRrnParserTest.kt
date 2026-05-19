package com.trana.identity.adapter

import com.trana.identity.adapter.ncp.KoreanRrnParser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import kotlin.test.assertEquals

class KoreanRrnParserTest {
    @Test
    fun parses1900sMaleWithGenderCode1() {
        val result = KoreanRrnParser.parse("8501011234567")
        assertEquals(LocalDate.of(1985, 1, 1), result.birthDate)
        assertEquals(Gender.MALE, result.gender)
        assertEquals(64, result.hash.length)
    }

    @Test
    fun parses2000sFemaleWithGenderCode4() {
        val result = KoreanRrnParser.parse("0501014234567")
        assertEquals(LocalDate.of(2005, 1, 1), result.birthDate)
        assertEquals(Gender.FEMALE, result.gender)
    }

    @Test
    fun acceptsRrnWithDashSeparator() {
        val result = KoreanRrnParser.parse("850101-1234567")
        assertEquals(LocalDate.of(1985, 1, 1), result.birthDate)
        assertEquals(Gender.MALE, result.gender)
    }

    @Test
    fun throwsOnInvalidLength() {
        assertThrows<IllegalArgumentException> {
            KoreanRrnParser.parse("85010112345")
        }
    }

    @Test
    fun hashIsDeterministicAcrossDashFormats() {
        val h1 = KoreanRrnParser.parse("8501011234567").hash
        val h2 = KoreanRrnParser.parse("850101-1234567").hash
        assertEquals(h1, h2)
    }
}
