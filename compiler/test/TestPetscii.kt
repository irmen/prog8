package prog8tests

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.expectError
import com.github.michaelbull.result.getOrElse
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.compiler.target.cbm.Petscii
import java.io.CharConversionException


class TestPetscii: FunSpec({

    test("testZero") {
        Petscii.encodePetscii("\u0000", true) shouldBe Ok(listOf<Short>(0))
        Petscii.encodePetscii("\u0000", false) shouldBe Ok(listOf<Short>(0))
        Petscii.decodePetscii(listOf(0), true) shouldBe "\u0000"
        Petscii.decodePetscii(listOf(0), false) shouldBe "\u0000"
    }

    test("testLowercase") {
        Petscii.encodePetscii("hello WORLD 123 @!£", true) shouldBe
                Ok(listOf<Short>(72, 69, 76, 76, 79, 32, 0xd7, 0xcf, 0xd2, 0xcc, 0xc4, 32, 49, 50, 51, 32, 64, 33, 0x5c))
        Petscii.encodePetscii("\uf11a", true) shouldBe Ok(listOf<Short>(0x12))   // reverse vid
        Petscii.encodePetscii("✓", true) shouldBe Ok(listOf<Short>(0xfa))
        withClue("expect lowercase error fallback") {
            Petscii.encodePetscii("π", true) shouldBe Ok(listOf<Short>(255))
            Petscii.encodePetscii("♥", true) shouldBe Ok(listOf<Short>(0xd3))
        }

        Petscii.decodePetscii(listOf(72, 0xd7, 0x5c, 0xfa, 0x12), true) shouldBe "hW£✓\uF11A"
    }

    test("testUppercase") {
        Petscii.encodePetscii("HELLO 123 @!£") shouldBe
                Ok(listOf<Short>(72, 69, 76, 76, 79, 32, 49, 50, 51, 32, 64, 33, 0x5c))
        Petscii.encodePetscii("\uf11a") shouldBe Ok(listOf<Short>(0x12))   // reverse vid
        Petscii.encodePetscii("♥") shouldBe Ok(listOf<Short>(0xd3))
        Petscii.encodePetscii("π") shouldBe Ok(listOf<Short>(0xff))
        withClue("expecting fallback") {
            Petscii.encodePetscii("✓") shouldBe Ok(listOf<Short>(250))
        }

        Petscii.decodePetscii(listOf(72, 0x5c, 0xd3, 0xff)) shouldBe "H£♥π"
    }

    test("testScreencodeLowercase") {
        Petscii.encodeScreencode("hello WORLD 123 @!£", true) shouldBe
                Ok(listOf<Short>(0x08, 0x05, 0x0c, 0x0c, 0x0f, 0x20, 0x57, 0x4f, 0x52, 0x4c, 0x44, 0x20, 0x31, 0x32, 0x33, 0x20, 0x00, 0x21, 0x1c))
        Petscii.encodeScreencode("✓", true) shouldBe Ok(listOf<Short>(0x7a))
        withClue("expect fallback") {
            Petscii.encodeScreencode("♥", true) shouldBe Ok(listOf<Short>(83))
            Petscii.encodeScreencode("π", true) shouldBe Ok(listOf<Short>(94))
        }

        Petscii.decodeScreencode(listOf(0x08, 0x57, 0x1c, 0x7a), true) shouldBe "hW£✓"
    }

    test("testScreencodeUppercase") {
        Petscii.encodeScreencode("WORLD 123 @!£") shouldBe
                Ok(listOf<Short>(0x17, 0x0f, 0x12, 0x0c, 0x04, 0x20, 0x31, 0x32, 0x33, 0x20, 0x00, 0x21, 0x1c))
        Petscii.encodeScreencode("♥") shouldBe Ok(listOf<Short>(0x53))
        Petscii.encodeScreencode("π") shouldBe Ok(listOf<Short>(0x5e))
        Petscii.encodeScreencode("HELLO") shouldBe Ok(listOf<Short>(8, 5, 12, 12, 15))
        withClue("expecting fallback") {
            Petscii.encodeScreencode("hello") shouldBe Ok(listOf<Short>(8, 5, 12, 12, 15))
            Petscii.encodeScreencode("✓") shouldBe Ok(listOf<Short>(122))
        }

        Petscii.decodeScreencode(listOf(0x17, 0x1c, 0x53, 0x5e)) shouldBe "W£♥π"
    }

    test("testErrorCases") {
        Petscii.encodePetscii("~", true).expectError { "shouldn't be able to encode tilde" }
        Petscii.encodePetscii("~", false).expectError { "shouldn't be able to encode tilde" }
        Petscii.encodeScreencode("~", true).expectError { "shouldn't be able to encode tilde" }
        Petscii.encodeScreencode("~", false).expectError { "shouldn't be able to encode tilde" }

        shouldThrow<CharConversionException> { Petscii.decodePetscii(listOf<Short>(-1), true) }
        shouldThrow<CharConversionException> { Petscii.decodePetscii(listOf<Short>(256), true) }
        shouldThrow<CharConversionException> { Petscii.decodePetscii(listOf<Short>(-1), false) }
        shouldThrow<CharConversionException> { Petscii.decodePetscii(listOf<Short>(256), false) }
        shouldThrow<CharConversionException> { Petscii.decodeScreencode(listOf<Short>(-1), true) }
        shouldThrow<CharConversionException> { Petscii.decodeScreencode(listOf<Short>(256), true) }
        shouldThrow<CharConversionException> { Petscii.decodeScreencode(listOf<Short>(-1), false) }
        shouldThrow<CharConversionException> { Petscii.decodeScreencode(listOf<Short>(256), false) }

        Petscii.scr2petscii(-1).expectError { "-1 should error" }
        Petscii.scr2petscii(256).expectError { "256 should error" }
        Petscii.petscii2scr(-1, true).expectError { "-1 should error" }
        Petscii.petscii2scr(256, true).expectError { "256 should error" }
        Petscii.petscii2scr(-1, false).expectError { "-1 should error" }
        Petscii.petscii2scr(256, false).expectError { "256 should error" }
    }

    test("testSpecialReplacements") {
        fun encodeP(c: Char, lower: Boolean) = Petscii.encodePetscii(c.toString(), lower).getOrElse { throw it }.single()
        fun encodeS(c: Char, lower: Boolean) = Petscii.encodeScreencode(c.toString(), lower).getOrElse { throw it }.single()

        Petscii.encodePetscii("`", false).expectError { "shouldn't have translation for backtick" }
        Petscii.encodePetscii("`", true).expectError { "shouldn't have translation for backtick" }
        Petscii.encodePetscii("~", false).expectError { "shouldn't have translation for tilde" }
        Petscii.encodePetscii("~", true).expectError { "shouldn't have translation for tilde" }

        encodeP('^', false) shouldBe 94
        encodeP('^', true) shouldBe 94
        encodeS('^', false) shouldBe 30
        encodeS('^', true) shouldBe 30
        encodeP('_', false) shouldBe 228
        encodeP('_', true) shouldBe 228
        encodeS('_', false) shouldBe 100
        encodeS('_', true) shouldBe 100
        encodeP('{', false) shouldBe 243
        encodeP('{', true) shouldBe 243
        encodeS('{', false) shouldBe 115
        encodeS('{', true) shouldBe 115
        encodeP('}', false) shouldBe 235
        encodeP('}', true) shouldBe 235
        encodeS('}', false) shouldBe 107
        encodeS('}', true) shouldBe 107
        encodeP('|', false) shouldBe 221
        encodeP('|', true) shouldBe 221
        encodeS('|', false) shouldBe 93
        encodeS('|', true) shouldBe 93
        encodeP('\\', false) shouldBe 205
        encodeP('\\', true) shouldBe 205
        encodeS('\\', false) shouldBe 77
        encodeS('\\', true) shouldBe 77
    }

    test("testBoxDrawingCharsEncoding") {
        fun encodeP(c: Char, lower: Boolean) = Petscii.encodePetscii(c.toString(), lower).getOrElse { throw it }.single()
        fun encodeS(c: Char, lower: Boolean) = Petscii.encodeScreencode(c.toString(), lower).getOrElse { throw it }.single()

        // pipe char
        encodeP('|', false) shouldBe 221
        encodeP('|', true) shouldBe 221
        encodeS('|', false) shouldBe 93
        encodeS('|', true) shouldBe 93
        // ... same as '│', 0x7D -> BOX DRAWINGS LIGHT VERTICAL
        encodeP('│', false) shouldBe 221
        encodeP('│', true) shouldBe 221
        encodeS('│', false) shouldBe 93
        encodeS('│', true) shouldBe 93

        // underscore
        encodeP('_', false) shouldBe 228
        encodeP('_', true) shouldBe 228
        encodeS('_', false) shouldBe 100
        encodeS('_', true) shouldBe 100
        // ... same as '▁',  0xE4 LOWER ONE EIGHTH BLOCK
        encodeP('▁', false) shouldBe 228
        encodeP('▁', true) shouldBe 228
        encodeS('▁', false) shouldBe 100
        encodeS('▁', true) shouldBe 100

        // ─    0xC0 -> BOX DRAWINGS LIGHT HORIZONTAL
        encodeP('─', false) shouldBe 192
        encodeP('─', true) shouldBe 192
        encodeS('─', false) shouldBe 64
        encodeS('─', true) shouldBe 64
        // │    0x62 -> BOX DRAWINGS LIGHT VERTICAL
        encodeP('│', false) shouldBe 221
        encodeP('│', true) shouldBe 221
        encodeS('│', false) shouldBe 93
        encodeS('│', true) shouldBe 93
    }

    test("testBoxDrawingCharsDecoding") {
        // ─    0xC0 -> BOX DRAWINGS LIGHT HORIZONTAL
        Petscii.decodePetscii(listOf(195), false).single() shouldBe '\uf13b' //"BOX DRAWINGS LIGHT HORIZONTAL ONE EIGHTH UP (CUS)"
        Petscii.decodePetscii(listOf(195), true).single() shouldBe 'C'
        Petscii.decodePetscii(listOf(192), false).single() shouldBe '─'
        Petscii.decodePetscii(listOf(192), true).single() shouldBe '─'
        Petscii.decodeScreencode(listOf(67), false).single() shouldBe '\uf13b' //"BOX DRAWINGS LIGHT HORIZONTAL ONE EIGHTH UP (CUS)"
        Petscii.decodeScreencode(listOf(67), true).single() shouldBe 'C'
        Petscii.decodeScreencode(listOf(64), false).single() shouldBe '─'
        Petscii.decodeScreencode(listOf(64), true).single() shouldBe '─'

        // │    0x62 -> BOX DRAWINGS LIGHT VERTICAL
        Petscii.decodePetscii(listOf(125), false).single() shouldBe '│'
        Petscii.decodePetscii(listOf(125), true).single() shouldBe '│'
        Petscii.decodePetscii(listOf(221), false).single() shouldBe '│'
        Petscii.decodePetscii(listOf(221), true).single() shouldBe '│'
        Petscii.decodeScreencode(listOf(93), false).single() shouldBe '│'
        Petscii.decodeScreencode(listOf(93), true).single() shouldBe '│'
        Petscii.decodeScreencode(listOf(66), false).single() shouldBe '\uf13c' // "BOX DRAWINGS LIGHT VERTICAL ONE EIGHTH LEFT (CUS)"
        Petscii.decodeScreencode(listOf(66), true).single() shouldBe 'B'
    }

})
