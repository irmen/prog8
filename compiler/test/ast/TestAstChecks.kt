package prog8tests.ast

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import prog8.code.target.C64Target
import prog8.code.target.Cx16Target
import prog8.code.target.VMTarget
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText


class TestAstChecks: FunSpec({

    val outputDir = tempdir().toPath()
    
    test("conditional expression w/float works") {
        val text = """
            %import floats
            main {
                sub start() {
                    uword xx
                    if xx+99.99 == xx+1.234 {
                        xx++
                    }
                }
            }
        """
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(C64Target(), true, text, outputDir, writeAssembly = true, errors=errors) shouldNotBe null
        errors.errors.size shouldBe 0
        errors.infos.size shouldBe 2
        errors.infos[0] shouldContain "converted to float"
        errors.infos[1] shouldContain "converted to float"
    }

    test("can't assign label or subroutine without using address-of") {
        val text = """
            main {
                sub start() {
            
            label:
                    uword @shared addr
                    addr = label
                    addr = thing
                    addr = &label
                    addr = &thing
                }
            
                sub thing() {
                }
            }
            """
        val errors = ErrorReporterForTests()
        compileText(C64Target(), true, text, outputDir, writeAssembly = true, errors=errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.warnings.size shouldBe 0
        errors.errors[0] shouldContain ":7:28: invalid assignment value"
        errors.errors[1] shouldContain ":8:28: invalid assignment value"
    }

    test("can't do str or array expression without using address-of") {
        val text = """
            %import textio
            main {
                sub start() {
                    ubyte[] array = [1,2,3,4]
                    str s1 = "test"
                    ubyte ff = 1
                    txt.print(s1+ff)
                    txt.print(array+ff)
                    txt.print_uwhex(s1+ff, true)
                    txt.print_uwhex(array+ff, true)
                }
            }
            """
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, text, outputDir, writeAssembly = false, errors=errors) shouldBe null
        errors.errors.filter { it.contains("missing &") }.size shouldBe 4
    }

    test("str or array expression with address-of") {
        val text = """
            %import textio
            main {
                sub start() {
                    ubyte[] array = [1,2,3,4]
                    str s1 = "test"
                    bool bb1, bb2
                    ubyte ff = 1
                    txt.print(&s1+ff)
                    txt.print(&array+ff)
                    txt.print_uwhex(&s1+ff, true)
                    txt.print_uwhex(&array+ff, true)
                    ; also good:
                    bb1 = (s1 == "derp")
                    bb2 = (s1 != "derp")
                }
            }
            """
        compileText(C64Target(), false, text, outputDir, writeAssembly = false) shouldNotBe null
    }

    test("const is not allowed on arrays") {
        val text = """
            main {
                sub start() {
                    const ubyte[5] a = [1,2,3,4,5]
                    a[2]=42
                }
            }
        """
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(C64Target(), true, text, outputDir, writeAssembly = true, errors=errors)
        errors.errors.size shouldBe 1
        errors.warnings.size shouldBe 0
        errors.errors[0] shouldContain "const can only be used"
    }

    test("array indexing is not allowed on a memory mapped variable") {
        val text = """
            main {
                sub start() {
                    &ubyte a = 10000
                    cx16.r0L = a[4]
                    a[4] = cx16.r1L
                }
            }
        """
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(C64Target(), true, text, outputDir, writeAssembly = true, errors=errors)
        errors.errors.size shouldBe 2
        errors.warnings.size shouldBe 0
        errors.errors[0] shouldContain "indexing requires"
        errors.errors[1] shouldContain "indexing requires"
    }

    test("unicode in identifier names is working") {
        val text = """
%import floats

main {
    ubyte приблизительно = 99
    ubyte นี่คือตัวอักษรภาษาไท = 42
    
    sub start() {
        str knäckebröd = "crunchy"  ; with composed form
        prt(knäckebröd)             ; with decomposed form
        printf(2*floats.π)
    }

    sub prt(str message) {
        приблизительно++
    }

    sub printf(float fl) {
        นี่คือตัวอักษรภาษาไท++
    }
}"""
        compileText(C64Target(), false, text, outputDir, writeAssembly = true)  shouldNotBe null
        compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)  shouldNotBe null
        compileText(VMTarget(), false, text, outputDir, writeAssembly = true)  shouldNotBe null
    }

    test("return with a statement instead of a value is a syntax error") {
        val src="""
main {

    sub invalid() {
        return cx16.r0++
    }

    sub start() {
        invalid()
    }
}"""
        val errors=ErrorReporterForTests()
        compileText(C64Target(), false, src, outputDir, writeAssembly = false, errors=errors)  shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "statement"
    }

    test("redefined variable name in single declaration is reported") {
        val src="""
main {
    sub start() {
        const ubyte count=11
        cx16.r0++
        ubyte count = 88        ; redefinition
        cx16.r0 = count
    }
}"""
        val errors=ErrorReporterForTests()
        compileText(C64Target(), false, src, outputDir, writeAssembly = false, errors=errors)  shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "name conflict"

        errors.clear()
        compileText(C64Target(), true, src, outputDir, writeAssembly = false, errors=errors)  shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "name conflict"
    }

    test("redefined variable name in multi declaration is reported") {
        val src="""
main {
    sub start() {
        ubyte i
        i++
        ubyte i, j              ; redefinition
        i++
        j++
    }
}
"""
        val errors=ErrorReporterForTests()
        compileText(C64Target(), false, src, outputDir, writeAssembly = false, errors=errors)  shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "name conflict"

        errors.clear()
        compileText(C64Target(), true, src, outputDir, writeAssembly = false, errors=errors)  shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "name conflict"
    }

    test("various range datatype checks allow differences in type") {
        val src="""
main {
    sub func() -> ubyte {
        cx16.r0++
        return cx16.r0L
    }

    sub start() {
        bool[256] @shared cells
        word starw
        byte bb
        uword uw
        ubyte ub

        starw = (240-64 as word) + func()

        for starw in 50 downto 10  {
            cx16.r0++
        }
        for starw in cx16.r0L downto 10  {
            cx16.r0++
        }

        for ub in 0 to len(cells)-1 {
            cx16.r0++
        }
        for ub in cx16.r0L to len(cells)-1 {
            cx16.r0++
        }
        for bb in 50 downto 10  {
            cx16.r0++
        }
        for bb in cx16.r0sL downto 10  {
            cx16.r0++
        }

        for starw in 500 downto 10  {
            cx16.r0++
        }
        for uw in 50 downto 10 {
            cx16.r0++
        }
        for uw in 500 downto 10 {
            cx16.r0++
        }
    }
}"""
        compileText(C64Target(), false, src, outputDir, writeAssembly = false) shouldNotBe null
        compileText(C64Target(), true, src, outputDir, writeAssembly = false) shouldNotBe null
    }

    test("reg params cannot be statusflag") {
        val src="""
main {
    sub start() {
        faulty(false)
    }

    sub faulty(bool flag @Pc) {
        cx16.r0++
    }
}"""

        // the syntax error is actually thrown by the parser, so we cannot catch it, but we know that there may not be a compilation result
        compileText(C64Target(), false, src, outputDir, writeAssembly = false) shouldBe null
    }

    test("reg params cannot be cpu register") {
        val src="""
main {
    sub start() {
        faulty(42)
    }

    sub faulty(byte arg @Y) {
        arg++
    }
}"""

        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, src, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "can only use R0-R15"
    }

    test("reg params must be all different") {
        val src="""
main {
    sub start() {
        faulty3(9999,55)
    }

    sub faulty3(uword arg @R1, ubyte arg2 @R1) {
        arg += arg2
    }
}"""

        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, src, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "register is used multiple times"
    }

    test("reg params R0-R15 are ok") {
        val src="""
main {
    sub start() {
        foo(42)
        bar(9999,55)
    }

    sub foo(ubyte arg @R2) {
        arg++
    }

    sub bar(uword arg @R0, ubyte arg2 @R1) {
        arg += arg2
    }
}"""

        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(C64Target(), false, src, outputDir, writeAssembly = false, errors = errors) shouldNotBe null
        errors.errors.size shouldBe 0
        errors.warnings.size shouldBe 2
        errors.warnings[0] shouldContain "footgun"
        errors.warnings[1] shouldContain "footgun"
    }

    test("reg params R0-R15 cannot be used for invalid types") {
        val src="""
main {
    sub func(str value @R1) {
        return
    }

    sub start() {
        func(true)
    }
}"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(C64Target(), false, src, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.warnings.size shouldBe 0
        errors.errors[0] shouldContain "requires integer or boolean type"
    }

    test("missing address of in expression operand") {
        val src="""
main {
    sub start() {
        str name = "foo"
        cx16.r0 =  name+2
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, src, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.warnings.size shouldBe 0
        errors.errors[0] shouldContain "missing &"
    }

    test("can't use uword[] as a parameter type give clear error") {
        val src="""
main {
    sub start() {
        uword[] @nosplit array = [1111,2222]
        funcw1(array)
        funcw1([1111,2222])
        funcw2(array)
        funcw2([1111,2222])
        funcb([11,22])
    }

    sub funcw1(uword[] ptr) {
        ; error
    }

    sub funcw2(uword ptr) {
        ; ok
    }

    sub funcb(ubyte[] ptr) {
        ; ok
    }
}
"""
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, src, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 3
        errors.warnings.size shouldBe 0
        errors.errors[0] shouldContain  ":5:16: argument 1 type mismatch"
        errors.errors[1] shouldContain  ":6:16: argument 1 type mismatch"
        errors.errors[2] shouldContain  ":12:16: this pass-by-reference type can't be used as a parameter type"
    }

    test("proper type checking for multi-value assigns") {
        val src="""
main {
    sub start() {
        bool bb
        ubyte ub
        uword uw
        uw, void = thing2()
        uw, bb = thing2()
        uw, ub = thing2()
    }

    asmsub thing2() -> ubyte @A, bool @Pc {
        %asm {{
            rts
        }}
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, src, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 4
        errors.errors[0] shouldContain  "can't assign returnvalue #1 to corresponding target; ubyte vs uword"
        errors.errors[1] shouldContain  "can't assign returnvalue #1 to corresponding target; ubyte vs uword"
        errors.errors[2] shouldContain  "can't assign returnvalue #1 to corresponding target; ubyte vs uword"
        errors.errors[3] shouldContain  "can't assign returnvalue #2 to corresponding target; bool vs ubyte"
    }

    test("multi assigns with too few result values from the function") {
        val src="""
main {
    sub start() {
        ubyte @shared x,y,z
        x,y,z = sys.progend()
        ubyte @shared k,l,m = sys.progend()
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), optimize=true, src, outputDir, writeAssembly=false, errors = errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "too few values: expected 3 got 1"
        errors.errors[1] shouldContain "too few values: expected 3 got 1"
    }

    test("correct errors for wrong string initialization value") {
        val src="""
main {

    sub start() {
        str minString1 = 1234
        str minString2 = func()
    }

    sub func() -> str {
        return "zz"
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), optimize=false, src, outputDir, writeAssembly=false, errors = errors) shouldBe null
        errors.errors.size shouldBe 3
        errors.errors[0] shouldContain "value type uword doesn't match target type str"
        errors.errors[1] shouldContain "string var must be initialized with a string literal"
        errors.errors[2] shouldContain "string var must be initialized with a string literal"
    }
})
