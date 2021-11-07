package prog8tests

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.expectError
import com.github.michaelbull.result.getOrElse
import io.kotest.core.spec.style.FunSpec
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import prog8.compiler.target.cbm.Petscii
import java.io.CharConversionException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


class TestPetscii: FunSpec({

    test("testZero") {
        assertThat(Petscii.encodePetscii("\u0000", true), equalTo(Ok(listOf<Short>(0))))
        assertThat(Petscii.encodePetscii("\u0000", false), equalTo(Ok(listOf<Short>(0))))
        assertThat(Petscii.decodePetscii(listOf(0), true), equalTo("\u0000"))
        assertThat(Petscii.decodePetscii(listOf(0), false), equalTo("\u0000"))
    }

    test("testLowercase") {
        assertThat(Petscii.encodePetscii("hello WORLD 123 @!£", true), equalTo(
                Ok(listOf<Short>(72, 69, 76, 76, 79, 32, 0xd7, 0xcf, 0xd2, 0xcc, 0xc4, 32, 49, 50, 51, 32, 64, 33, 0x5c))))
        assertThat(Petscii.encodePetscii("\uf11a", true), equalTo(Ok(listOf<Short>(0x12))))   // reverse vid
        assertThat(Petscii.encodePetscii("✓", true), equalTo(Ok(listOf<Short>(0xfa))))
        assertThat("expect lowercase error fallback", Petscii.encodePetscii("π", true), equalTo(Ok(listOf<Short>(255))))
        assertThat("expect lowercase error fallback", Petscii.encodePetscii("♥", true), equalTo(Ok(listOf<Short>(0xd3))))

        assertThat(Petscii.decodePetscii(listOf(72, 0xd7, 0x5c, 0xfa, 0x12), true), equalTo("hW£✓\uF11A"))
    }

    test("testUppercase") {
        assertThat(Petscii.encodePetscii("HELLO 123 @!£"), equalTo(
                Ok(listOf<Short>(72, 69, 76, 76, 79, 32, 49, 50, 51, 32, 64, 33, 0x5c))))
        assertThat(Petscii.encodePetscii("\uf11a"), equalTo(Ok(listOf<Short>(0x12))))   // reverse vid
        assertThat(Petscii.encodePetscii("♥"), equalTo(Ok(listOf<Short>(0xd3))))
        assertThat(Petscii.encodePetscii("π"), equalTo(Ok(listOf<Short>(0xff))))
        assertThat("expecting fallback", Petscii.encodePetscii("✓"), equalTo(Ok(listOf<Short>(250))))

        assertThat(Petscii.decodePetscii(listOf(72, 0x5c, 0xd3, 0xff)), equalTo("H£♥π"))
    }

    test("testScreencodeLowercase") {
        assertThat(Petscii.encodeScreencode("hello WORLD 123 @!£", true), equalTo(
                Ok(listOf<Short>(0x08, 0x05, 0x0c, 0x0c, 0x0f, 0x20, 0x57, 0x4f, 0x52, 0x4c, 0x44, 0x20, 0x31, 0x32, 0x33, 0x20, 0x00, 0x21, 0x1c))
        ))
        assertThat(Petscii.encodeScreencode("✓", true), equalTo(Ok(listOf<Short>(0x7a))))
        assertThat("expect fallback", Petscii.encodeScreencode("♥", true), equalTo(Ok(listOf<Short>(83))))
        assertThat("expect fallback", Petscii.encodeScreencode("π", true), equalTo(Ok(listOf<Short>(94))))

        assertThat(Petscii.decodeScreencode(listOf(0x08, 0x57, 0x1c, 0x7a), true), equalTo("hW£✓"))
    }

    test("testScreencodeUppercase") {
        assertThat(Petscii.encodeScreencode("WORLD 123 @!£"), equalTo(
                Ok(listOf<Short>(0x17, 0x0f, 0x12, 0x0c, 0x04, 0x20, 0x31, 0x32, 0x33, 0x20, 0x00, 0x21, 0x1c))))
        assertThat(Petscii.encodeScreencode("♥"), equalTo(Ok(listOf<Short>(0x53))))
        assertThat(Petscii.encodeScreencode("π"), equalTo(Ok(listOf<Short>(0x5e))))
        assertThat(Petscii.encodeScreencode("HELLO"), equalTo(Ok(listOf<Short>(8, 5, 12, 12, 15))))
        assertThat("expecting fallback", Petscii.encodeScreencode("hello"), equalTo(Ok(listOf<Short>(8, 5, 12, 12, 15))))
        assertThat("expecting fallback", Petscii.encodeScreencode("✓"), equalTo(Ok(listOf<Short>(122))))

        assertThat(Petscii.decodeScreencode(listOf(0x17, 0x1c, 0x53, 0x5e)), equalTo("W£♥π"))
    }

    test("testErrorCases") {
        Petscii.encodePetscii("~", true).expectError { "shouldn't be able to encode tilde" }
        Petscii.encodePetscii("~", false).expectError { "shouldn't be able to encode tilde" }
        Petscii.encodeScreencode("~", true).expectError { "shouldn't be able to encode tilde" }
        Petscii.encodeScreencode("~", false).expectError { "shouldn't be able to encode tilde" }

        assertFailsWith<CharConversionException> { Petscii.decodePetscii(listOf<Short>(-1), true) }
        assertFailsWith<CharConversionException> { Petscii.decodePetscii(listOf<Short>(256), true) }
        assertFailsWith<CharConversionException> { Petscii.decodePetscii(listOf<Short>(-1), false) }
        assertFailsWith<CharConversionException> { Petscii.decodePetscii(listOf<Short>(256), false) }
        assertFailsWith<CharConversionException> { Petscii.decodeScreencode(listOf<Short>(-1), true) }
        assertFailsWith<CharConversionException> { Petscii.decodeScreencode(listOf<Short>(256), true) }
        assertFailsWith<CharConversionException> { Petscii.decodeScreencode(listOf<Short>(-1), false) }
        assertFailsWith<CharConversionException> { Petscii.decodeScreencode(listOf<Short>(256), false) }

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

        assertEquals(94, encodeP('^', false))
        assertEquals(94, encodeP('^', true))
        assertEquals(30, encodeS('^', false))
        assertEquals(30, encodeS('^', true))
        assertEquals(228, encodeP('_', false))
        assertEquals(228, encodeP('_', true))
        assertEquals(100, encodeS('_', false))
        assertEquals(100, encodeS('_', true))
        assertEquals(243, encodeP('{', false))
        assertEquals(243, encodeP('{', true))
        assertEquals(115, encodeS('{', false))
        assertEquals(115, encodeS('{', true))
        assertEquals(235, encodeP('}', false))
        assertEquals(235, encodeP('}', true))
        assertEquals(107, encodeS('}', false))
        assertEquals(107, encodeS('}', true))
        assertEquals(221, encodeP('|', false))
        assertEquals(221, encodeP('|', true))
        assertEquals(93, encodeS('|', false))
        assertEquals(93, encodeS('|', true))
        assertEquals(205, encodeP('\\', false))
        assertEquals(205, encodeP('\\', true))
        assertEquals(77, encodeS('\\', false))
        assertEquals(77, encodeS('\\', true))
    }

    test("testBoxDrawingCharsEncoding") {
        fun encodeP(c: Char, lower: Boolean) = Petscii.encodePetscii(c.toString(), lower).getOrElse { throw it }.single()
        fun encodeS(c: Char, lower: Boolean) = Petscii.encodeScreencode(c.toString(), lower).getOrElse { throw it }.single()

        // pipe char
        assertEquals(221, encodeP('|', false))
        assertEquals(221, encodeP('|', true))
        assertEquals(93, encodeS('|', false))
        assertEquals(93, encodeS('|', true))
        // ... same as '│', 0x7D -> BOX DRAWINGS LIGHT VERTICAL
        assertEquals(221, encodeP('│', false))
        assertEquals(221, encodeP('│', true))
        assertEquals(93, encodeS('│', false))
        assertEquals(93, encodeS('│', true))

        // underscore
        assertEquals(228, encodeP('_', false))
        assertEquals(228, encodeP('_', true))
        assertEquals(100, encodeS('_', false))
        assertEquals(100, encodeS('_', true))
        // ... same as '▁',  0xE4 LOWER ONE EIGHTH BLOCK
        assertEquals(228, encodeP('▁', false))
        assertEquals(228, encodeP('▁', true))
        assertEquals(100, encodeS('▁', false))
        assertEquals(100, encodeS('▁', true))

        // ─    0xC0 -> BOX DRAWINGS LIGHT HORIZONTAL
        assertEquals(192, encodeP('─', false))
        assertEquals(192, encodeP('─', true))
        assertEquals(64, encodeS('─', false))
        assertEquals(64, encodeS('─', true))
        // │    0x62 -> BOX DRAWINGS LIGHT VERTICAL
        assertEquals(221, encodeP('│', false))
        assertEquals(221, encodeP('│', true))
        assertEquals(93, encodeS('│', false))
        assertEquals(93, encodeS('│', true))
    }

    test("testBoxDrawingCharsDecoding") {
        // ─    0xC0 -> BOX DRAWINGS LIGHT HORIZONTAL
        assertEquals('\uf13b', Petscii.decodePetscii(listOf(195), false).single(), "BOX DRAWINGS LIGHT HORIZONTAL ONE EIGHTH UP (CUS)")
        assertEquals('C', Petscii.decodePetscii(listOf(195), true).single())
        assertEquals('─', Petscii.decodePetscii(listOf(192), false).single())
        assertEquals('─', Petscii.decodePetscii(listOf(192), true).single())
        assertEquals('\uf13b', Petscii.decodeScreencode(listOf(67), false).single(), "BOX DRAWINGS LIGHT HORIZONTAL ONE EIGHTH UP (CUS)")
        assertEquals('C', Petscii.decodeScreencode(listOf(67), true).single())
        assertEquals('─', Petscii.decodeScreencode(listOf(64), false).single())
        assertEquals('─', Petscii.decodeScreencode(listOf(64), true).single())

        // │    0x62 -> BOX DRAWINGS LIGHT VERTICAL
        assertEquals('│', Petscii.decodePetscii(listOf(125), false).single())
        assertEquals('│', Petscii.decodePetscii(listOf(125), true).single())
        assertEquals('│', Petscii.decodePetscii(listOf(221), false).single())
        assertEquals('│', Petscii.decodePetscii(listOf(221), true).single())
        assertEquals('│', Petscii.decodeScreencode(listOf(93), false).single())
        assertEquals('│', Petscii.decodeScreencode(listOf(93), true).single())
        assertEquals('\uf13c', Petscii.decodeScreencode(listOf(66), false).single(), "BOX DRAWINGS LIGHT VERTICAL ONE EIGHTH LEFT (CUS)")
        assertEquals('B', Petscii.decodeScreencode(listOf(66), true).single())
    }

})
