package prog8tests

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import prog8.ast.toHex
import prog8.compiler.target.c64.C64MachineDefinition.FLOAT_MAX_NEGATIVE
import prog8.compiler.target.c64.C64MachineDefinition.FLOAT_MAX_POSITIVE
import prog8.compiler.target.c64.C64MachineDefinition.Mflpt5
import prog8.compilerinterface.InternalCompilerException


class TestNumbers: FunSpec({
    test("testToHex") {
        0.toHex() shouldBe "0"
        1.toHex() shouldBe "1"
        1.234.toHex() shouldBe "1"
        10.toHex() shouldBe "10"
        10.99.toHex() shouldBe "10"
        15.toHex() shouldBe "15"
        16.toHex() shouldBe "\$10"
        255.toHex() shouldBe "\$ff"
        256.toHex() shouldBe "\$0100"
        20060.toHex() shouldBe "\$4e5c"
        50050.toHex() shouldBe "\$c382"
        65535.toHex() shouldBe "\$ffff"
        65535L.toHex() shouldBe "\$ffff"
        0.toHex() shouldBe "0"
        (-1).toHex() shouldBe "-1"
        (-1.234).toHex() shouldBe "-1"
        (-10).toHex() shouldBe "-10"
        (-10.99).toHex() shouldBe "-10"
        (-15).toHex() shouldBe "-15"
        (-16).toHex() shouldBe "-\$10"
        (-255).toHex() shouldBe "-\$ff"
        (-256).toHex() shouldBe "-\$0100"
        (-20060).toHex() shouldBe "-\$4e5c"
        (-50050).toHex() shouldBe "-\$c382"
        (-65535).toHex() shouldBe "-\$ffff"
        (-65535L).toHex() shouldBe "-\$ffff"
        shouldThrow<IllegalArgumentException> { 65536.toHex()  }
        shouldThrow<IllegalArgumentException> { 65536L.toHex()  }
    }

    test("testFloatToMflpt5") {
        Mflpt5.fromNumber(0) shouldBe Mflpt5(0x00, 0x00, 0x00, 0x00, 0x00)
        Mflpt5.fromNumber(3.141592653) shouldBe Mflpt5(0x82, 0x49, 0x0F, 0xDA, 0xA1)
        Mflpt5.fromNumber(3.141592653589793) shouldBe Mflpt5(0x82, 0x49, 0x0F, 0xDA, 0xA2)
        Mflpt5.fromNumber(32768) shouldBe Mflpt5(0x90, 0x00, 0x00, 0x00, 0x00)
        Mflpt5.fromNumber(-32768) shouldBe Mflpt5(0x90, 0x80, 0x00, 0x00, 0x00)
        Mflpt5.fromNumber(1) shouldBe Mflpt5(0x81, 0x00, 0x00, 0x00, 0x00)
        Mflpt5.fromNumber(0.7071067812) shouldBe Mflpt5(0x80, 0x35, 0x04, 0xF3, 0x34)
        Mflpt5.fromNumber(0.7071067811865476) shouldBe Mflpt5(0x80, 0x35, 0x04, 0xF3, 0x33)
        Mflpt5.fromNumber(1.4142135624) shouldBe Mflpt5(0x81, 0x35, 0x04, 0xF3, 0x34)
        Mflpt5.fromNumber(1.4142135623730951) shouldBe Mflpt5(0x81, 0x35, 0x04, 0xF3, 0x33)
        Mflpt5.fromNumber(-.5) shouldBe Mflpt5(0x80, 0x80, 0x00, 0x00, 0x00)
        Mflpt5.fromNumber(0.69314718061) shouldBe Mflpt5(0x80, 0x31, 0x72, 0x17, 0xF8)
        Mflpt5.fromNumber(0.6931471805599453) shouldBe Mflpt5(0x80, 0x31, 0x72, 0x17, 0xF7)
        Mflpt5.fromNumber(10) shouldBe Mflpt5(0x84, 0x20, 0x00, 0x00, 0x00)
        Mflpt5.fromNumber(1000000000) shouldBe Mflpt5(0x9E, 0x6E, 0x6B, 0x28, 0x00)
        Mflpt5.fromNumber(.5) shouldBe Mflpt5(0x80, 0x00, 0x00, 0x00, 0x00)
        Mflpt5.fromNumber(1.4426950408889634) shouldBe Mflpt5(0x81, 0x38, 0xAA, 0x3B, 0x29)
        Mflpt5.fromNumber(1.5707963267948966) shouldBe Mflpt5(0x81, 0x49, 0x0F, 0xDA, 0xA2)
        Mflpt5.fromNumber(6.283185307179586) shouldBe Mflpt5(0x83, 0x49, 0x0F, 0xDA, 0xA2)
        Mflpt5.fromNumber(.25) shouldBe Mflpt5(0x7F, 0x00, 0x00, 0x00, 0x00)
        Mflpt5.fromNumber(123.45678e22) shouldBe Mflpt5(0xd1, 0x02, 0xb7, 0x06, 0xfb)
        Mflpt5.fromNumber(-123.45678e-22) shouldBe Mflpt5(0x3e, 0xe9, 0x34, 0x09, 0x1b)
    }

    test("testFloatRange") {
        Mflpt5.fromNumber(FLOAT_MAX_POSITIVE) shouldBe Mflpt5(0xff, 0x7f, 0xff, 0xff, 0xff)
        Mflpt5.fromNumber(FLOAT_MAX_NEGATIVE) shouldBe Mflpt5(0xff, 0xff, 0xff, 0xff, 0xff)
        Mflpt5.fromNumber(1.7e-38) shouldBe Mflpt5(0x03, 0x39, 0x1d, 0x15, 0x63)
        Mflpt5.fromNumber(1.7e-39) shouldBe Mflpt5(0x00, 0x00, 0x00, 0x00, 0x00)
        Mflpt5.fromNumber(-1.7e-38) shouldBe Mflpt5(0x03, 0xb9, 0x1d, 0x15, 0x63)
        Mflpt5.fromNumber(-1.7e-39) shouldBe Mflpt5(0x00, 0x00, 0x00, 0x00, 0x00)
        shouldThrow<InternalCompilerException> { Mflpt5.fromNumber(1.7014118346e+38) }
        shouldThrow<InternalCompilerException> { Mflpt5.fromNumber(-1.7014118346e+38) }
        shouldThrow<InternalCompilerException> { Mflpt5.fromNumber(1.7014118347e+38) }
        shouldThrow<InternalCompilerException> { Mflpt5.fromNumber(-1.7014118347e+38) }
    }

    test("testMflpt5ToFloat") {
        val epsilon=0.000000001

        Mflpt5(0x00, 0x00, 0x00, 0x00, 0x00).toDouble() shouldBe 0.0
        Mflpt5(0x82, 0x49, 0x0F, 0xDA, 0xA1).toDouble() shouldBe(3.141592653 plusOrMinus epsilon)
        Mflpt5(0x82, 0x49, 0x0F, 0xDA, 0xA2).toDouble() shouldBe(3.141592653589793 plusOrMinus epsilon)
        Mflpt5(0x90, 0x00, 0x00, 0x00, 0x00).toDouble() shouldBe 32768.0
        Mflpt5(0x90, 0x80, 0x00, 0x00, 0x00).toDouble() shouldBe -32768.0
        Mflpt5(0x81, 0x00, 0x00, 0x00, 0x00).toDouble() shouldBe 1.0
        Mflpt5(0x80, 0x35, 0x04, 0xF3, 0x34).toDouble() shouldBe(0.7071067812 plusOrMinus epsilon)
        Mflpt5(0x80, 0x35, 0x04, 0xF3, 0x33).toDouble() shouldBe(0.7071067811865476 plusOrMinus epsilon)
        Mflpt5(0x81, 0x35, 0x04, 0xF3, 0x34).toDouble() shouldBe(1.4142135624 plusOrMinus epsilon)
        Mflpt5(0x81, 0x35, 0x04, 0xF3, 0x33).toDouble() shouldBe(1.4142135623730951 plusOrMinus epsilon)
        Mflpt5(0x80, 0x80, 0x00, 0x00, 0x00).toDouble() shouldBe -.5
        Mflpt5(0x80, 0x31, 0x72, 0x17, 0xF8).toDouble() shouldBe(0.69314718061 plusOrMinus epsilon)
        Mflpt5(0x80, 0x31, 0x72, 0x17, 0xF7).toDouble() shouldBe(0.6931471805599453 plusOrMinus epsilon)
        Mflpt5(0x84, 0x20, 0x00, 0x00, 0x00).toDouble() shouldBe 10.0
        Mflpt5(0x9E, 0x6E, 0x6B, 0x28, 0x00).toDouble() shouldBe 1000000000.0
        Mflpt5(0x80, 0x00, 0x00, 0x00, 0x00).toDouble() shouldBe .5
        Mflpt5(0x81, 0x38, 0xAA, 0x3B, 0x29).toDouble() shouldBe(1.4426950408889634 plusOrMinus epsilon)
        Mflpt5(0x81, 0x49, 0x0F, 0xDA, 0xA2).toDouble() shouldBe(1.5707963267948966 plusOrMinus epsilon)
        Mflpt5(0x83, 0x49, 0x0F, 0xDA, 0xA2).toDouble() shouldBe(6.283185307179586 plusOrMinus epsilon)
        Mflpt5(0x7F, 0x00, 0x00, 0x00, 0x00).toDouble() shouldBe .25
        Mflpt5(0xd1, 0x02, 0xb7, 0x06, 0xfb).toDouble() shouldBe(123.45678e22 plusOrMinus 1.0e15)
        Mflpt5(0x3e, 0xe9, 0x34, 0x09, 0x1b).toDouble() shouldBe(-123.45678e-22 plusOrMinus epsilon)
    }
})
