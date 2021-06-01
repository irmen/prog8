package prog8tests

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.base.DataType
import prog8.ast.base.Position
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.expressions.StringLiteralValue
import prog8.compiler.target.cbm.Petscii
import kotlin.test.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestPetscii {

    @Test
    fun testZero() {
        assertThat(Petscii.encodePetscii("\u0000", true), equalTo(listOf<Short>(0)))
        assertThat(Petscii.encodePetscii("\u0000", false), equalTo(listOf<Short>(0)))
        assertThat(Petscii.decodePetscii(listOf(0), true), equalTo("\u0000"))
        assertThat(Petscii.decodePetscii(listOf(0), false), equalTo("\u0000"))
    }

    @Test
    fun testLowercase() {
        assertThat(Petscii.encodePetscii("hello WORLD 123 @!£", true), equalTo(
                listOf<Short>(72, 69, 76, 76, 79, 32, 0xd7, 0xcf, 0xd2, 0xcc, 0xc4, 32, 49, 50, 51, 32, 64, 33, 0x5c)))
        assertThat(Petscii.encodePetscii("\uf11a", true), equalTo(listOf<Short>(0x12)))   // reverse vid
        assertThat(Petscii.encodePetscii("✓", true), equalTo(listOf<Short>(0xfa)))
        assertThat("expect lowercase error fallback", Petscii.encodePetscii("π", true), equalTo(listOf<Short>(255)))
        assertThat("expect lowercase error fallback", Petscii.encodePetscii("♥", true), equalTo(listOf<Short>(0xd3)))

        assertThat(Petscii.decodePetscii(listOf(72, 0xd7, 0x5c, 0xfa, 0x12), true), equalTo("hW£✓\uF11A"))
        assertFailsWith<ArrayIndexOutOfBoundsException> { Petscii.decodePetscii(listOf(-1), true) }
        assertFailsWith<ArrayIndexOutOfBoundsException> { Petscii.decodePetscii(listOf(256), true) }
    }

    @Test
    fun testUppercase() {
        assertThat(Petscii.encodePetscii("HELLO 123 @!£"), equalTo(
                listOf<Short>(72, 69, 76, 76, 79, 32, 49, 50, 51, 32, 64, 33, 0x5c)))
        assertThat(Petscii.encodePetscii("\uf11a"), equalTo(listOf<Short>(0x12)))   // reverse vid
        assertThat(Petscii.encodePetscii("♥"), equalTo(listOf<Short>(0xd3)))
        assertThat(Petscii.encodePetscii("π"), equalTo(listOf<Short>(0xff)))
        assertThat("expecting fallback", Petscii.encodePetscii("✓"), equalTo(listOf<Short>(250)))

        assertThat(Petscii.decodePetscii(listOf(72, 0x5c, 0xd3, 0xff)), equalTo("H£♥π"))
        assertFailsWith<ArrayIndexOutOfBoundsException> { Petscii.decodePetscii(listOf(-1)) }
        assertFailsWith<ArrayIndexOutOfBoundsException> { Petscii.decodePetscii(listOf(256)) }
    }

    @Test
    fun testScreencodeLowercase() {
        assertThat(Petscii.encodeScreencode("hello WORLD 123 @!£", true), equalTo(
                listOf<Short>(0x08, 0x05, 0x0c, 0x0c, 0x0f, 0x20, 0x57, 0x4f, 0x52, 0x4c, 0x44, 0x20, 0x31, 0x32, 0x33, 0x20, 0x00, 0x21, 0x1c)
        ))
        assertThat(Petscii.encodeScreencode("✓", true), equalTo(listOf<Short>(0x7a)))
        assertThat("expect fallback", Petscii.encodeScreencode("♥", true), equalTo(listOf<Short>(83)))
        assertThat("expect fallback", Petscii.encodeScreencode("π", true), equalTo(listOf<Short>(94)))

        assertThat(Petscii.decodeScreencode(listOf(0x08, 0x57, 0x1c, 0x7a), true), equalTo("hW£✓"))
        assertFailsWith<ArrayIndexOutOfBoundsException> { Petscii.decodeScreencode(listOf(-1), true) }
        assertFailsWith<ArrayIndexOutOfBoundsException> { Petscii.decodeScreencode(listOf(256), true) }
    }

    @Test
    fun testScreencodeUppercase() {
        assertThat(Petscii.encodeScreencode("WORLD 123 @!£"), equalTo(
                listOf<Short>(0x17, 0x0f, 0x12, 0x0c, 0x04, 0x20, 0x31, 0x32, 0x33, 0x20, 0x00, 0x21, 0x1c)))
        assertThat(Petscii.encodeScreencode("♥"), equalTo(listOf<Short>(0x53)))
        assertThat(Petscii.encodeScreencode("π"), equalTo(listOf<Short>(0x5e)))
        assertThat(Petscii.encodeScreencode("HELLO"), equalTo(listOf<Short>(8, 5, 12, 12, 15)))
        assertThat("expecting fallback", Petscii.encodeScreencode("hello"), equalTo(listOf<Short>(8, 5, 12, 12, 15)))
        assertThat("expecting fallback", Petscii.encodeScreencode("✓"), equalTo(listOf<Short>(122)))

        assertThat(Petscii.decodeScreencode(listOf(0x17, 0x1c, 0x53, 0x5e)), equalTo("W£♥π"))
        assertFailsWith<ArrayIndexOutOfBoundsException> { Petscii.decodeScreencode(listOf(-1)) }
        assertFailsWith<ArrayIndexOutOfBoundsException> { Petscii.decodeScreencode(listOf(256)) }
    }

    @Test
    fun testLiteralValueComparisons() {
        val ten = NumericLiteralValue(DataType.UWORD, 10, Position.DUMMY)
        val nine = NumericLiteralValue(DataType.UBYTE, 9, Position.DUMMY)
        assertEquals(ten, ten)
        assertNotEquals(ten, nine)
        assertFalse(ten != ten)
        assertTrue(ten != nine)

        assertTrue(ten > nine)
        assertTrue(ten >= nine)
        assertTrue(ten >= ten)
        assertFalse(ten > ten)

        assertFalse(ten < nine)
        assertFalse(ten <= nine)
        assertTrue(ten <= ten)
        assertFalse(ten < ten)

        val abc = StringLiteralValue("abc", false, Position.DUMMY)
        val abd = StringLiteralValue("abd", false, Position.DUMMY)
        assertEquals(abc, abc)
        assertTrue(abc!=abd)
        assertFalse(abc!=abc)
    }
}
