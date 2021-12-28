package prog8tests

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.expectError
import com.github.michaelbull.result.getOrElse
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.codegen.target.cbm.Petscii


class TestPetscii: FunSpec({

    test("testZero") {
        Petscii.encodePetscii("\u0000", true) shouldBe Ok(listOf<UByte>(0u))
        Petscii.encodePetscii("\u0000", false) shouldBe Ok(listOf<UByte>(0u))
        Petscii.decodePetscii(listOf(0u), true) shouldBe "\u0000"
        Petscii.decodePetscii(listOf(0u), false) shouldBe "\u0000"
    }

    test("testLowercase") {
        Petscii.encodePetscii("hello WORLD 123 @!£", true) shouldBe
                Ok(listOf<UByte>(72u, 69u, 76u, 76u, 79u, 32u, 0xd7u, 0xcfu, 0xd2u, 0xccu, 0xc4u, 32u, 49u, 50u, 51u, 32u, 64u, 33u, 0x5cu))
        Petscii.encodePetscii("\uf11a", true) shouldBe Ok(listOf<UByte>(0x12u))   // reverse vid
        Petscii.encodePetscii("✓", true) shouldBe Ok(listOf<UByte>(0xfau))
        withClue("expect lowercase error fallback") {
            Petscii.encodePetscii("π", true) shouldBe Ok(listOf<UByte>(255u))
            Petscii.encodePetscii("♥", true) shouldBe Ok(listOf<UByte>(0xd3u))
        }

        Petscii.decodePetscii(listOf(72u, 0xd7u, 0x5cu, 0xfau, 0x12u), true) shouldBe "hW£✓\uF11A"
    }

    test("testUppercase") {
        Petscii.encodePetscii("HELLO 123 @!£") shouldBe
                Ok(listOf<UByte>(72u, 69u, 76u, 76u, 79u, 32u, 49u, 50u, 51u, 32u, 64u, 33u, 0x5cu))
        Petscii.encodePetscii("\uf11a") shouldBe Ok(listOf<UByte>(0x12u))   // reverse vid
        Petscii.encodePetscii("♥") shouldBe Ok(listOf<UByte>(0xd3u))
        Petscii.encodePetscii("π") shouldBe Ok(listOf<UByte>(0xffu))
        withClue("expecting fallback") {
            Petscii.encodePetscii("✓") shouldBe Ok(listOf<UByte>(250u))
        }

        Petscii.decodePetscii(listOf(72u, 0x5cu, 0xd3u, 0xffu)) shouldBe "H£♥π"
    }

    test("testScreencodeLowercase") {
        Petscii.encodeScreencode("hello WORLD 123 @!£", true) shouldBe
                Ok(listOf<UByte>(0x08u, 0x05u, 0x0cu, 0x0cu, 0x0fu, 0x20u, 0x57u, 0x4fu, 0x52u, 0x4cu, 0x44u, 0x20u, 0x31u, 0x32u, 0x33u, 0x20u, 0x00u, 0x21u, 0x1cu))
        Petscii.encodeScreencode("✓", true) shouldBe Ok(listOf<UByte>(0x7au))
        withClue("expect fallback") {
            Petscii.encodeScreencode("♥", true) shouldBe Ok(listOf<UByte>(83u))
            Petscii.encodeScreencode("π", true) shouldBe Ok(listOf<UByte>(94u))
        }

        Petscii.decodeScreencode(listOf(0x08u, 0x57u, 0x1cu, 0x7au), true) shouldBe "hW£✓"
    }

    test("testScreencodeUppercase") {
        Petscii.encodeScreencode("WORLD 123 @!£") shouldBe
                Ok(listOf<UByte>(0x17u, 0x0fu, 0x12u, 0x0cu, 0x04u, 0x20u, 0x31u, 0x32u, 0x33u, 0x20u, 0x00u, 0x21u, 0x1cu))
        Petscii.encodeScreencode("♥") shouldBe Ok(listOf<UByte>(0x53u))
        Petscii.encodeScreencode("π") shouldBe Ok(listOf<UByte>(0x5eu))
        Petscii.encodeScreencode("HELLO") shouldBe Ok(listOf<UByte>(8u, 5u, 12u, 12u, 15u))
        withClue("expecting fallback") {
            Petscii.encodeScreencode("hello") shouldBe Ok(listOf<UByte>(8u, 5u, 12u, 12u, 15u))
            Petscii.encodeScreencode("✓") shouldBe Ok(listOf<UByte>(122u))
        }

        Petscii.decodeScreencode(listOf(0x17u, 0x1cu, 0x53u, 0x5eu)) shouldBe "W£♥π"
    }

    test("testErrorCases") {
        Petscii.encodePetscii("~", true).expectError { "shouldn't be able to encode tilde" }
        Petscii.encodePetscii("~", false).expectError { "shouldn't be able to encode tilde" }
        Petscii.encodeScreencode("~", true).expectError { "shouldn't be able to encode tilde" }
        Petscii.encodeScreencode("~", false).expectError { "shouldn't be able to encode tilde" }
    }

    test("testSpecialReplacements") {
        fun encodeP(c: Char, lower: Boolean) = Petscii.encodePetscii(c.toString(), lower).getOrElse { throw it }.single()
        fun encodeS(c: Char, lower: Boolean) = Petscii.encodeScreencode(c.toString(), lower).getOrElse { throw it }.single()

        Petscii.encodePetscii("`", false).expectError { "shouldn't have translation for backtick" }
        Petscii.encodePetscii("`", true).expectError { "shouldn't have translation for backtick" }
        Petscii.encodePetscii("~", false).expectError { "shouldn't have translation for tilde" }
        Petscii.encodePetscii("~", true).expectError { "shouldn't have translation for tilde" }

        encodeP('^', false) shouldBe 94u
        encodeP('^', true) shouldBe 94u
        encodeS('^', false) shouldBe 30u
        encodeS('^', true) shouldBe 30u
        encodeP('_', false) shouldBe 228u
        encodeP('_', true) shouldBe 228u
        encodeS('_', false) shouldBe 100u
        encodeS('_', true) shouldBe 100u
        encodeP('{', false) shouldBe 243u
        encodeP('{', true) shouldBe 243u
        encodeS('{', false) shouldBe 115u
        encodeS('{', true) shouldBe 115u
        encodeP('}', false) shouldBe 235u
        encodeP('}', true) shouldBe 235u
        encodeS('}', false) shouldBe 107u
        encodeS('}', true) shouldBe 107u
        encodeP('|', false) shouldBe 221u
        encodeP('|', true) shouldBe 221u
        encodeS('|', false) shouldBe 93u
        encodeS('|', true) shouldBe 93u
        encodeP('\\', false) shouldBe 205u
        encodeP('\\', true) shouldBe 205u
        encodeS('\\', false) shouldBe 77u
        encodeS('\\', true) shouldBe 77u
    }

    test("testBoxDrawingCharsEncoding") {
        fun encodeP(c: Char, lower: Boolean) = Petscii.encodePetscii(c.toString(), lower).getOrElse { throw it }.single()
        fun encodeS(c: Char, lower: Boolean) = Petscii.encodeScreencode(c.toString(), lower).getOrElse { throw it }.single()

        // pipe char
        encodeP('|', false) shouldBe 221u
        encodeP('|', true) shouldBe 221u
        encodeS('|', false) shouldBe 93u
        encodeS('|', true) shouldBe 93u
        // ... same as '│', 0x7D -> BOX DRAWINGS LIGHT VERTICAL
        encodeP('│', false) shouldBe 221u
        encodeP('│', true) shouldBe 221u
        encodeS('│', false) shouldBe 93u
        encodeS('│', true) shouldBe 93u

        // underscore
        encodeP('_', false) shouldBe 228u
        encodeP('_', true) shouldBe 228u
        encodeS('_', false) shouldBe 100u
        encodeS('_', true) shouldBe 100u
        // ... same as '▁',  0xE4 LOWER ONE EIGHTH BLOCK
        encodeP('▁', false) shouldBe 228u
        encodeP('▁', true) shouldBe 228u
        encodeS('▁', false) shouldBe 100u
        encodeS('▁', true) shouldBe 100u

        // ─    0xC0 -> BOX DRAWINGS LIGHT HORIZONTAL
        encodeP('─', false) shouldBe 192u
        encodeP('─', true) shouldBe 192u
        encodeS('─', false) shouldBe 64u
        encodeS('─', true) shouldBe 64u
        // │    0x62 -> BOX DRAWINGS LIGHT VERTICAL
        encodeP('│', false) shouldBe 221u
        encodeP('│', true) shouldBe 221u
        encodeS('│', false) shouldBe 93u
        encodeS('│', true) shouldBe 93u
    }

    test("testBoxDrawingCharsDecoding") {
        // ─    0xC0 -> BOX DRAWINGS LIGHT HORIZONTAL
        Petscii.decodePetscii(listOf(195u), false).single() shouldBe '\uf13b' //"BOX DRAWINGS LIGHT HORIZONTAL ONE EIGHTH UP (CUS)"
        Petscii.decodePetscii(listOf(195u), true).single() shouldBe 'C'
        Petscii.decodePetscii(listOf(192u), false).single() shouldBe '─'
        Petscii.decodePetscii(listOf(192u), true).single() shouldBe '─'
        Petscii.decodeScreencode(listOf(67u), false).single() shouldBe '\uf13b' //"BOX DRAWINGS LIGHT HORIZONTAL ONE EIGHTH UP (CUS)"
        Petscii.decodeScreencode(listOf(67u), true).single() shouldBe 'C'
        Petscii.decodeScreencode(listOf(64u), false).single() shouldBe '─'
        Petscii.decodeScreencode(listOf(64u), true).single() shouldBe '─'

        // │    0x62 -> BOX DRAWINGS LIGHT VERTICAL
        Petscii.decodePetscii(listOf(125u), false).single() shouldBe '│'
        Petscii.decodePetscii(listOf(125u), true).single() shouldBe '│'
        Petscii.decodePetscii(listOf(221u), false).single() shouldBe '│'
        Petscii.decodePetscii(listOf(221u), true).single() shouldBe '│'
        Petscii.decodeScreencode(listOf(93u), false).single() shouldBe '│'
        Petscii.decodeScreencode(listOf(93u), true).single() shouldBe '│'
        Petscii.decodeScreencode(listOf(66u), false).single() shouldBe '\uf13c' // "BOX DRAWINGS LIGHT VERTICAL ONE EIGHTH LEFT (CUS)"
        Petscii.decodeScreencode(listOf(66u), true).single() shouldBe 'B'
    }

})
