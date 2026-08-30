package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import prog8.code.target.Cx16Target
import prog8.code.target.VMTarget
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText

class TestReverseForIteration: FunSpec({

    val outputDir = tempdir().toPath()

    test("array forward step 1 compiles") {
        val result = compileText(Cx16Target(), false, """
            main {
                sub start() {
                    ubyte[] arr = [1,2,3,4]
                    ubyte x
                    for x in arr step 1 {
                        x++
                    }
                    for x in arr {
                        x++
                    }
                }
            }
        """, outputDir)
        result shouldNotBe null
    }

    test("array reverse step -1 compiles - ubyte array") {
        val result = compileText(Cx16Target(), false, """
            main {
                sub start() {
                    ubyte[] arr = [10,20,30,40]
                    ubyte x
                    for x in arr step -1 {
                        x++
                    }
                }
            }
        """, outputDir)
        result shouldNotBe null
    }

    test("array reverse step -1 compiles - uword array") {
        val result = compileText(Cx16Target(), false, """
            main {
                sub start() {
                    uword[] arr = [1000,2000,3000]
                    uword x
                    for x in arr step -1 {
                        x++
                    }
                }
            }
        """, outputDir)
        result shouldNotBe null
    }

    test("string reverse step -1 compiles - string variable") {
        val result = compileText(Cx16Target(), false, """
            main {
                sub start() {
                    str w = "hello"
                    ubyte c
                    for c in w step -1 {
                        c++
                    }
                }
            }
        """, outputDir)
        result shouldNotBe null
    }

    test("string reverse step -1 compiles - string literal") {
        val result = compileText(Cx16Target(), false, """
            main {
                sub start() {
                    ubyte c
                    for c in "hello" step -1 {
                        c++
                    }
                }
            }
        """, outputDir)
        result shouldNotBe null
    }

    test("array reverse step -1 execution via VM - ubyte array sum") {
        val result = compileText(VMTarget(), false, """
            main {
                sub start() {
                    ubyte @shared failures = 0
                    ubyte[] arr = [1,2,3,4]
                    ubyte @shared sum = 0
                    for ubyte x in arr step -1 {
                        sum += x
                    }
                    ubyte[] @shared rev = [0,0,0,0]
                    ubyte @shared idx = 0
                    for ubyte y in arr step -1 {
                        rev[idx] = y
                        idx++
                    }
                    if rev[0]!=4 { failures++ }
                    if rev[1]!=3 { failures++ }
                    if rev[2]!=2 { failures++ }
                    if rev[3]!=1 { failures++ }
                }
            }
        """, outputDir)!!
        result.codegenAst shouldNotBe null
    }

    test("string reverse step -1 execution via VM - check order") {
        val result = compileText(VMTarget(), false, """
            main {
                sub start() {
                    ubyte @shared failures = 0
                    str s = "abcd"
                    ubyte @shared idx = 0
                    ubyte[] @shared rev = [0,0,0,0]
                    for ubyte c in s step -1 {
                        rev[idx] = c
                        idx++
                    }
                    if rev[0] != 100 { failures++ }
                    if rev[1] != 99 { failures++ }
                    if rev[2] != 98 { failures++ }
                    if rev[3] != 97 { failures++ }
                }
            }
        """, outputDir)!!
        result.codegenAst shouldNotBe null
    }

    test("array step 2 should fail") {
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), false, """
            main {
                sub start() {
                    ubyte[] arr = [1,2,3]
                    ubyte x
                    for x in arr step 2 {
                        x++
                    }
                }
            }
        """, outputDir, errors, writeAssembly = false) shouldBe null
        errors.errors[0] shouldContain "step for non-range iterable must be 1 or -1"
    }

    test("array step 0 should fail") {
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), false, """
            main {
                sub start() {
                    ubyte[] arr = [1,2,3]
                    ubyte x
                    for x in arr step 0 {
                        x++
                    }
                }
            }
        """, outputDir, errors, writeAssembly = false) shouldBe null
        errors.errors[0] shouldContain "step for non-range iterable must be 1 or -1"
    }

    test("string step 2 should fail") {
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), false, """
            main {
                sub start() {
                    str s = "hello"
                    ubyte c
                    for c in s step 2 {
                        c++
                    }
                }
            }
        """, outputDir, errors, writeAssembly = false) shouldBe null
        errors.errors[0] shouldContain "step for non-range iterable must be 1 or -1"
    }

    test("range step combined with for step should fail") {
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), false, """
            main {
                sub start() {
                    ubyte i
                    for i in 1 to 10 step 2 step -1 {
                        i++
                    }
                }
            }
        """, outputDir, errors, writeAssembly = false) shouldBe null
        errors.errors[0] shouldContain "step on for-loop cannot be combined with range step"
    }

    test("array without step still works") {
        val result = compileText(Cx16Target(), false, """
            main {
                sub start() {
                    ubyte[] arr = [5,6,7]
                    ubyte x
                    for x in arr {
                        x++
                    }
                }
            }
        """, outputDir)
        result shouldNotBe null
    }
})
