package com.trana.common.crypto

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class Sha256HasherTest {
    @Test
    fun hashHexOfEmptyStringMatchesKnownValue() {
        // NIST FIPS 180-4 test vector
        Assertions.assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256Hasher.hashHex(""),
        )
    }

    @Test
    fun hashHexOfAbcMatchesKnownValue() {
        // NIST FIPS 180-4 test vector
        Assertions.assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256Hasher.hashHex("abc"),
        )
    }

    @Test
    fun hashHexIsDeterministic() {
        val a = Sha256Hasher.hashHex("동일 입력")
        val b = Sha256Hasher.hashHex("동일 입력")
        Assertions.assertEquals(a, b)
    }

    @Test
    fun hashHexReturnsSixtyFourHexCharacters() {
        Assertions.assertEquals(SHA256_HEX_LENGTH, Sha256Hasher.hashHex("any input").length)
    }

    companion object {
        private const val SHA256_HEX_LENGTH = 64
    }
}
