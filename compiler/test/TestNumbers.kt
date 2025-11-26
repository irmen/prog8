package prog8tests.compiler

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import prog8.code.ast.PtAssignment
import prog8.code.ast.PtNumber
import prog8.code.core.InternalCompilerException
import prog8.code.core.toHex
import prog8.code.target.C64Target
import prog8.code.target.Mflpt5
import prog8.code.target.VMTarget
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText


class TestNumbers: FunSpec({

    val outputDir = tempdir().toPath()

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
        (65536).toHex() shouldBe "\$00010000"
        (-65536).toHex() shouldBe "-\$00010000"
    }

    test("testFloatToMflpt5") {
        Mflpt5.fromNumber(0) shouldBe Mflpt5(0x00u, 0x00u, 0x00u, 0x00u, 0x00u)
        Mflpt5.fromNumber(3.141592653) shouldBe Mflpt5(0x82u, 0x49u, 0x0Fu, 0xDAu, 0xA1u)
        Mflpt5.fromNumber(3.141592653589793) shouldBe Mflpt5(0x82u, 0x49u, 0x0Fu, 0xDAu, 0xA2u)
        Mflpt5.fromNumber(32768) shouldBe Mflpt5(0x90u, 0x00u, 0x00u, 0x00u, 0x00u)
        Mflpt5.fromNumber(-32768) shouldBe Mflpt5(0x90u, 0x80u, 0x00u, 0x00u, 0x00u)
        Mflpt5.fromNumber(1) shouldBe Mflpt5(0x81u, 0x00u, 0x00u, 0x00u, 0x00u)
        Mflpt5.fromNumber(0.7071067812) shouldBe Mflpt5(0x80u, 0x35u, 0x04u, 0xF3u, 0x34u)
        Mflpt5.fromNumber(0.7071067811865476) shouldBe Mflpt5(0x80u, 0x35u, 0x04u, 0xF3u, 0x33u)
        Mflpt5.fromNumber(1.4142135624) shouldBe Mflpt5(0x81u, 0x35u, 0x04u, 0xF3u, 0x34u)
        Mflpt5.fromNumber(1.4142135623730951) shouldBe Mflpt5(0x81u, 0x35u, 0x04u, 0xF3u, 0x33u)
        Mflpt5.fromNumber(-.5) shouldBe Mflpt5(0x80u, 0x80u, 0x00u, 0x00u, 0x00u)
        Mflpt5.fromNumber(0.69314718061) shouldBe Mflpt5(0x80u, 0x31u, 0x72u, 0x17u, 0xF8u)
        Mflpt5.fromNumber(0.6931471805599453) shouldBe Mflpt5(0x80u, 0x31u, 0x72u, 0x17u, 0xF7u)
        Mflpt5.fromNumber(10) shouldBe Mflpt5(0x84u, 0x20u, 0x00u, 0x00u, 0x00u)
        Mflpt5.fromNumber(1000000000) shouldBe Mflpt5(0x9Eu, 0x6Eu, 0x6Bu, 0x28u, 0x00u)
        Mflpt5.fromNumber(.5) shouldBe Mflpt5(0x80u, 0x00u, 0x00u, 0x00u, 0x00u)
        Mflpt5.fromNumber(1.4426950408889634) shouldBe Mflpt5(0x81u, 0x38u, 0xAAu, 0x3Bu, 0x29u)
        Mflpt5.fromNumber(1.5707963267948966) shouldBe Mflpt5(0x81u, 0x49u, 0x0Fu, 0xDAu, 0xA2u)
        Mflpt5.fromNumber(6.283185307179586) shouldBe Mflpt5(0x83u, 0x49u, 0x0Fu, 0xDAu, 0xA2u)
        Mflpt5.fromNumber(.25) shouldBe Mflpt5(0x7Fu, 0x00u, 0x00u, 0x00u, 0x00u)
        Mflpt5.fromNumber(123.45678e22) shouldBe Mflpt5(0xd1u, 0x02u, 0xb7u, 0x06u, 0xfbu)
        Mflpt5.fromNumber(-123.45678e-22) shouldBe Mflpt5(0x3eu, 0xe9u, 0x34u, 0x09u, 0x1bu)
    }

    test("testFloatRange") {
        Mflpt5.fromNumber(Mflpt5.FLOAT_MAX_POSITIVE) shouldBe Mflpt5(0xffu, 0x7fu, 0xffu, 0xffu, 0xffu)
        Mflpt5.fromNumber(Mflpt5.FLOAT_MAX_NEGATIVE) shouldBe Mflpt5(0xffu, 0xffu, 0xffu, 0xffu, 0xffu)
        Mflpt5.fromNumber(1.7e-38) shouldBe Mflpt5(0x03u, 0x39u, 0x1du, 0x15u, 0x63u)
        Mflpt5.fromNumber(1.7e-39) shouldBe Mflpt5(0x00u, 0x00u, 0x00u, 0x00u, 0x00u)
        Mflpt5.fromNumber(-1.7e-38) shouldBe Mflpt5(0x03u, 0xb9u, 0x1du, 0x15u, 0x63u)
        Mflpt5.fromNumber(-1.7e-39) shouldBe Mflpt5(0x00u, 0x00u, 0x00u, 0x00u, 0x00u)
        shouldThrow<InternalCompilerException> { Mflpt5.fromNumber(1.7014118346e+38) }
        shouldThrow<InternalCompilerException> { Mflpt5.fromNumber(-1.7014118346e+38) }
        shouldThrow<InternalCompilerException> { Mflpt5.fromNumber(1.7014118347e+38) }
        shouldThrow<InternalCompilerException> { Mflpt5.fromNumber(-1.7014118347e+38) }
    }

    test("testMflpt5ToFloat") {
        val epsilon=0.000000001

        Mflpt5(0x00u, 0x00u, 0x00u, 0x00u, 0x00u).toDouble() shouldBe 0.0
        Mflpt5(0x82u, 0x49u, 0x0Fu, 0xDAu, 0xA1u).toDouble() shouldBe(3.141592653 plusOrMinus epsilon)
        Mflpt5(0x82u, 0x49u, 0x0Fu, 0xDAu, 0xA2u).toDouble() shouldBe(3.141592653589793 plusOrMinus epsilon)
        Mflpt5(0x90u, 0x00u, 0x00u, 0x00u, 0x00u).toDouble() shouldBe 32768.0
        Mflpt5(0x90u, 0x80u, 0x00u, 0x00u, 0x00u).toDouble() shouldBe -32768.0
        Mflpt5(0x81u, 0x00u, 0x00u, 0x00u, 0x00u).toDouble() shouldBe 1.0
        Mflpt5(0x80u, 0x35u, 0x04u, 0xF3u, 0x34u).toDouble() shouldBe(0.7071067812 plusOrMinus epsilon)
        Mflpt5(0x80u, 0x35u, 0x04u, 0xF3u, 0x33u).toDouble() shouldBe(0.7071067811865476 plusOrMinus epsilon)
        Mflpt5(0x81u, 0x35u, 0x04u, 0xF3u, 0x34u).toDouble() shouldBe(1.4142135624 plusOrMinus epsilon)
        Mflpt5(0x81u, 0x35u, 0x04u, 0xF3u, 0x33u).toDouble() shouldBe(1.4142135623730951 plusOrMinus epsilon)
        Mflpt5(0x80u, 0x80u, 0x00u, 0x00u, 0x00u).toDouble() shouldBe -.5
        Mflpt5(0x80u, 0x31u, 0x72u, 0x17u, 0xF8u).toDouble() shouldBe(0.69314718061 plusOrMinus epsilon)
        Mflpt5(0x80u, 0x31u, 0x72u, 0x17u, 0xF7u).toDouble() shouldBe(0.6931471805599453 plusOrMinus epsilon)
        Mflpt5(0x84u, 0x20u, 0x00u, 0x00u, 0x00u).toDouble() shouldBe 10.0
        Mflpt5(0x9Eu, 0x6Eu, 0x6Bu, 0x28u, 0x00u).toDouble() shouldBe 1000000000.0
        Mflpt5(0x80u, 0x00u, 0x00u, 0x00u, 0x00u).toDouble() shouldBe .5
        Mflpt5(0x81u, 0x38u, 0xAAu, 0x3Bu, 0x29u).toDouble() shouldBe(1.4426950408889634 plusOrMinus epsilon)
        Mflpt5(0x81u, 0x49u, 0x0Fu, 0xDAu, 0xA2u).toDouble() shouldBe(1.5707963267948966 plusOrMinus epsilon)
        Mflpt5(0x83u, 0x49u, 0x0Fu, 0xDAu, 0xA2u).toDouble() shouldBe(6.283185307179586 plusOrMinus epsilon)
        Mflpt5(0x7Fu, 0x00u, 0x00u, 0x00u, 0x00u).toDouble() shouldBe .25
        Mflpt5(0xd1u, 0x02u, 0xb7u, 0x06u, 0xfbu).toDouble() shouldBe(123.45678e22 plusOrMinus 1.0e15)
        Mflpt5(0x3eu, 0xe9u, 0x34u, 0x09u, 0x1bu).toDouble() shouldBe(-123.45678e-22 plusOrMinus epsilon)
    }

    test("implicit float conversion warning if enabled") {
        val src="""
            %option enable_floats
            main {
                sub start() {
                    uword xx = 10
                    if xx+99 == 1.23456
                        xx++
                    if xx+99 == 1234567.8
                        xx++
                }
            }
        """
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(C64Target(), true, src, outputDir, writeAssembly = false, errors=errors) shouldNotBe null
        errors.errors.size shouldBe 0
        errors.infos.size shouldBe 2
        errors.infos[0] shouldContain "converted to float"
        errors.infos[1] shouldContain "converted to float"
    }

    test("implicit float conversion error if not enabled") {
        val src="""
            main {
                sub start() {
                    uword xx = 10
                    if xx+99 == 1.23456
                        xx++
                    if xx+99 == 1234567.8
                        xx++
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(C64Target(), true, src, outputDir, writeAssembly = false, errors=errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.warnings.size shouldBe 0
        errors.errors[0] shouldContain "converted to float"
        errors.errors[1] shouldContain "converted to float"
    }

    test("out of range number assignments") {
        val src="""
            main {
                sub start() {
                    uword @shared qq = ${'$'}2ff33
                    cx16.r0 = ${'$'}1fc0f
                    cx16.r0L = 1234
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(C64Target(), true, src, outputDir, writeAssembly = false, errors=errors) shouldBe null
        errors.errors.size shouldBe 6
        errors.warnings.size shouldBe 0
        errors.errors[0] shouldContain "out of range"
        errors.errors[1] shouldContain "doesn't match"
        errors.errors[2] shouldContain "out of range"
        errors.errors[3] shouldContain "doesn't match"
        errors.errors[4] shouldContain "out of range"
        errors.errors[5] shouldContain "cannot assign word to byte"
    }

    test("large numeric literals still ok if actual value is small") {
        val src="""
            main {
                sub start() {
                    cx16.r1L = %000000000001
                    cx16.r2L = ${'$'}000000000001
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(C64Target(), true, src, outputDir, writeAssembly = false, errors=errors) shouldNotBe null
    }

    test("big numbers okay in const expressions if result fits") {
        val src="""
            main {
                sub start() {
                    uword @shared qq = ${'$'}2ff33 >> 4
                    cx16.r0 = ${'$'}1fc0f >> 4
                }
            }
        """
        compileText(C64Target(), true, src, outputDir, writeAssembly = false) shouldNotBe null
    }

    test("signed negative numbers not implicitly cast to unsigned") {
        val src="""
            main {
                sub start() {
                    uword uw1 = -32768
                    uword uw = -1
                    ubyte ub = -1
                    uw = -2 as uword
                    ub = -2 as ubyte 
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, src, outputDir, writeAssembly = false, errors=errors) shouldBe null
        errors.errors.size shouldBe 6
        errors.errors[0] shouldContain "out of range"
        errors.errors[1] shouldContain "word doesn't match"
        errors.errors[2] shouldContain "out of range"
        errors.errors[3] shouldContain "byte doesn't match"
        errors.errors[4] shouldContain "out of range"
        errors.errors[5] shouldContain "byte doesn't match"
    }


    test("biggest long numbers") {
        val src= $$"""
main {
    sub start() {
        long @shared l1, l2

        l1 = $7fffffff
        l2 = $80000000
        l1 = -2147483648
        l2 = -2147483649     ; will be truncated
        l1 = $abcd1234
        l2 = -$7fffffff
        l1 = $80000001
        l2 = -$80
    }
}"""
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        val result = compileText(VMTarget(), false, src, outputDir)!!
        val st = result.codegenAst!!.entrypoint()!!
        st.children.size shouldBe 13
        ((st.children[4] as PtAssignment).value as PtNumber).number shouldBe 2147483647
        ((st.children[5] as PtAssignment).value as PtNumber).number shouldBe -2147483648
        ((st.children[6] as PtAssignment).value as PtNumber).number shouldBe -2147483648
        ((st.children[7] as PtAssignment).value as PtNumber).number shouldBe 2147483647
        ((st.children[8] as PtAssignment).value as PtNumber).number shouldBe -1412623820
        ((st.children[9] as PtAssignment).value as PtNumber).number shouldBe -2147483647
        ((st.children[10] as PtAssignment).value as PtNumber).number shouldBe -2147483647
        ((st.children[11] as PtAssignment).value as PtNumber).number shouldBe -128
    }
})
