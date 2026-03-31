package prog8tests.codecore

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.code.core.escape
import prog8.code.core.toHex
import prog8.code.core.unescape

/**
 * Unit tests for conversion functions in codeCore.
 * Focus on toHex boundaries and escape/unescape roundtrips.
 */
class TestConversions: FunSpec({

    // ============================================================================
    // toHex() Tests
    // ============================================================================

    test("toHex for 0-15 (decimal output)") {
        0.toHex() shouldBe "0"
        10.toHex() shouldBe "10"
        15.toHex() shouldBe "15"
    }

    test("toHex for 16-255 (hex with $)") {
        16.toHex() shouldBe "\$10"
        255.toHex() shouldBe "\$ff"
    }

    test("toHex for 256-65535 (4 digit hex)") {
        256.toHex() shouldBe "\$0100"
        65535.toHex() shouldBe "\$ffff"
    }

    test("toHex for larger numbers (8 digit hex)") {
        65536.toHex() shouldBe "\$00010000"
        0x12345678.toHex() shouldBe "\$12345678"
    }

    test("toHex for negative numbers") {
        (-1).toHex() shouldBe "-1"
        (-255).toHex() shouldBe "-\$ff"
        Int.MIN_VALUE.toHex() shouldBe "\$80000000"
    }

    test("UInt.toHex") {
        0u.toHex() shouldBe "0"
        15u.toHex() shouldBe "15"
        255u.toHex() shouldBe "\$ff"
        65535u.toHex() shouldBe "\$ffff"
    }

    // ============================================================================
    // escape() Tests
    // ============================================================================

    test("escape for plain string") {
        "hello".escape() shouldBe "hello"
    }

    test("escape for tab and newline") {
        "hello\tworld".escape() shouldBe "hello\\tworld"
        "hello\nworld".escape() shouldBe "hello\\nworld"
    }

    test("escape for double quote") {
        "hello\"world".escape() shouldBe "hello\\\"world"
    }

    test("escape for unicode outside passthrough range") {
        "€".escape() shouldBe "\\u20ac"
    }

    test("escape for passthrough characters (\\u8000-\\u80ff)") {
        "\u8000".escape() shouldBe "\\x00"
        "\u80ff".escape() shouldBe "\\xff"
    }

    // ============================================================================
    // unescape() Tests
    // ============================================================================

    test("unescape for plain string") {
        "hello".unescape() shouldBe "hello"
    }

    test("unescape for tab and newline") {
        "hello\\tworld".unescape() shouldBe "hello\tworld"
        "hello\\nworld".unescape() shouldBe "hello\nworld"
    }

    test("unescape for double quote") {
        "hello\\\"world".unescape() shouldBe "hello\"world"
    }

    test("unescape for unicode") {
        "\\u0041".unescape() shouldBe "A"
        "\\u20ac".unescape() shouldBe "€"
    }

    // ============================================================================
    // Roundtrip Tests
    // ============================================================================

    test("escape then unescape roundtrip") {
        val original = "hello\t\nworld\"test"
        original.escape().unescape() shouldBe original
    }
})
