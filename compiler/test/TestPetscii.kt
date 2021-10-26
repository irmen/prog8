package prog8tests

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.expectError
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.compiler.target.cbm.Petscii
import java.io.CharConversionException
import kotlin.test.assertFailsWith


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestPetscii {

    @Test
    fun testZero() {
        assertThat(Petscii.encodePetscii("\u0000", true), equalTo(Ok(listOf<Short>(0))))
        assertThat(Petscii.encodePetscii("\u0000", false), equalTo(Ok(listOf<Short>(0))))
        assertThat(Petscii.decodePetscii(listOf(0), true), equalTo("\u0000"))
        assertThat(Petscii.decodePetscii(listOf(0), false), equalTo("\u0000"))
    }

    @Test
    fun testLowercase() {
        assertThat(Petscii.encodePetscii("hello WORLD 123 @!£", true), equalTo(
                Ok(listOf<Short>(72, 69, 76, 76, 79, 32, 0xd7, 0xcf, 0xd2, 0xcc, 0xc4, 32, 49, 50, 51, 32, 64, 33, 0x5c))))
        assertThat(Petscii.encodePetscii("\uf11a", true), equalTo(Ok(listOf<Short>(0x12))))   // reverse vid
        assertThat(Petscii.encodePetscii("✓", true), equalTo(Ok(listOf<Short>(0xfa))))
        assertThat("expect lowercase error fallback", Petscii.encodePetscii("π", true), equalTo(Ok(listOf<Short>(255))))
        assertThat("expect lowercase error fallback", Petscii.encodePetscii("♥", true), equalTo(Ok(listOf<Short>(0xd3))))

        assertThat(Petscii.decodePetscii(listOf(72, 0xd7, 0x5c, 0xfa, 0x12), true), equalTo("hW£✓\uF11A"))
    }

    @Test
    fun testUppercase() {
        assertThat(Petscii.encodePetscii("HELLO 123 @!£"), equalTo(
                Ok(listOf<Short>(72, 69, 76, 76, 79, 32, 49, 50, 51, 32, 64, 33, 0x5c))))
        assertThat(Petscii.encodePetscii("\uf11a"), equalTo(Ok(listOf<Short>(0x12))))   // reverse vid
        assertThat(Petscii.encodePetscii("♥"), equalTo(Ok(listOf<Short>(0xd3))))
        assertThat(Petscii.encodePetscii("π"), equalTo(Ok(listOf<Short>(0xff))))
        assertThat("expecting fallback", Petscii.encodePetscii("✓"), equalTo(Ok(listOf<Short>(250))))

        assertThat(Petscii.decodePetscii(listOf(72, 0x5c, 0xd3, 0xff)), equalTo("H£♥π"))
    }

    @Test
    fun testScreencodeLowercase() {
        assertThat(Petscii.encodeScreencode("hello WORLD 123 @!£", true), equalTo(
                Ok(listOf<Short>(0x08, 0x05, 0x0c, 0x0c, 0x0f, 0x20, 0x57, 0x4f, 0x52, 0x4c, 0x44, 0x20, 0x31, 0x32, 0x33, 0x20, 0x00, 0x21, 0x1c))
        ))
        assertThat(Petscii.encodeScreencode("✓", true), equalTo(Ok(listOf<Short>(0x7a))))
        assertThat("expect fallback", Petscii.encodeScreencode("♥", true), equalTo(Ok(listOf<Short>(83))))
        assertThat("expect fallback", Petscii.encodeScreencode("π", true), equalTo(Ok(listOf<Short>(94))))

        assertThat(Petscii.decodeScreencode(listOf(0x08, 0x57, 0x1c, 0x7a), true), equalTo("hW£✓"))
    }

    @Test
    fun testScreencodeUppercase() {
        assertThat(Petscii.encodeScreencode("WORLD 123 @!£"), equalTo(
                Ok(listOf<Short>(0x17, 0x0f, 0x12, 0x0c, 0x04, 0x20, 0x31, 0x32, 0x33, 0x20, 0x00, 0x21, 0x1c))))
        assertThat(Petscii.encodeScreencode("♥"), equalTo(Ok(listOf<Short>(0x53))))
        assertThat(Petscii.encodeScreencode("π"), equalTo(Ok(listOf<Short>(0x5e))))
        assertThat(Petscii.encodeScreencode("HELLO"), equalTo(Ok(listOf<Short>(8, 5, 12, 12, 15))))
        assertThat("expecting fallback", Petscii.encodeScreencode("hello"), equalTo(Ok(listOf<Short>(8, 5, 12, 12, 15))))
        assertThat("expecting fallback", Petscii.encodeScreencode("✓"), equalTo(Ok(listOf<Short>(122))))

        assertThat(Petscii.decodeScreencode(listOf(0x17, 0x1c, 0x53, 0x5e)), equalTo("W£♥π"))
    }

    @Test
    fun testErrorCases() {
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
}
