package prog8tests.codegeneration

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import prog8.code.core.AssemblyError
import prog8.code.target.Cx16Target
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText
import prog8tests.helpers.simulate

class TestVariableStepForLoops6502: FunSpec({
    val outputDir = tempdir().toPath()

    fun run(source: String) = compileText(Cx16Target(), false, source.trimIndent(), outputDir)!!.simulate()

    fun assertWord(machine: razorvine.ksim65.testing.TestMachine, address: Int, value: Int) {
        machine.assertMemory(address, value and 0xff)
        machine.assertMemory(address + 1, (value shr 8) and 0xff)
    }

    test("unsigned byte ascending") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &ubyte result = $0200
                sub start() {
                    ubyte @shared step = 3
                    ubyte i
                    ubyte sum = 0
                    for i in 0 to 10 step step {
                        sum += i
                    }
                    result = sum
                    poweroff = 1
                }
            }
        """)
        machine.assertMemory(0x0200, 18)
    }

    test("signed negative step with unsigned byte descending") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &ubyte result = $0201
                sub start() {
                    byte @shared step = -2
                    ubyte i
                    ubyte sum = 0
                    for i in 10 downto 0 step step {
                        sum += i
                    }
                    result = sum
                    poweroff = 1
                }
            }
        """)
        machine.assertMemory(0x0201, 30)
    }

    test("unsigned word ascending") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &uword result = $0202
                sub start() {
                    uword @shared step = 100
                    uword i
                    uword sum = 0
                    for i in 0 to 500 step step {
                        sum += i
                    }
                    result = sum
                    poweroff = 1
                }
            }
        """)
        assertWord(machine, 0x0202, 1500)
    }

    test("signed word ascending") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &word count = $0204
                &word result = $0206
                sub start() {
                    word @shared step = 4
                    word i
                    word sum = 0
                    for i in -10 to 10 step step {
                        count += 1
                        sum += i
                    }
                    result = sum
                    poweroff = 1
                }
            }
        """)
        assertWord(machine, 0x0204, 6)
        assertWord(machine, 0x0206, 0)
    }

    test("zero step") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &ubyte result = $0208
                sub start() {
                    ubyte @shared step = 0
                    ubyte i
                    for i in 0 to 10 step step {
                        result++
                    }
                    poweroff = 1
                }
            }
        """)
        machine.assertMemory(0x0208, 0)
    }

    test("wrong direction positive and negative") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &ubyte positive = $0209
                &ubyte negative = $020a
                sub start() {
                    ubyte @shared positiveStep = 1
                    byte @shared negativeStep = -1
                    ubyte i
                    for i in 10 to 0 step positiveStep {
                        positive++
                    }
                    for i in 0 to 10 step negativeStep {
                        negative++
                    }
                    poweroff = 1
                }
            }
        """)
        machine.assertMemory(0x0209, 0)
        machine.assertMemory(0x020a, 0)
    }

    test("from equals to") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &ubyte result = $020b
                sub start() {
                    ubyte @shared step = 5
                    ubyte i
                    for i in 7 to 7 step step {
                        result++
                    }
                    poweroff = 1
                }
            }
        """)
        machine.assertMemory(0x020b, 1)
    }

    test("body mutation of step does not change the loop increment") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &ubyte result = $020c
                ubyte @shared step = 2
                sub start() {
                    ubyte i
                    for i in 0 to 10 step step {
                        result += i
                        step = 99
                    }
                    poweroff = 1
                }
            }
        """)
        machine.assertMemory(0x020c, 30)
    }

    test("fixed width ubyte wrap stops at the bound") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &ubyte result = $020d
                sub start() {
                    ubyte @shared step = 3
                    ubyte i
                    for i in 254 to 255 step step {
                        result++
                    }
                    poweroff = 1
                }
            }
        """)
        machine.assertMemory(0x020d, 1)
    }

    test("nested break exits only the inner loop") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &ubyte result = $020e
                sub start() {
                    ubyte @shared step = 1
                    ubyte i
                    ubyte j
                    for i in 0 to 5 step step {
                        for j in 0 to 5 step step {
                            result++
                            if j == 2
                                break
                        }
                    }
                    poweroff = 1
                }
            }
        """)
        machine.assertMemory(0x020e, 18)
    }

    test("non-constant bounds") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &ubyte result = $020f
                ubyte @shared a = 1
                ubyte @shared b = 9
                sub start() {
                    ubyte @shared step = 2
                    ubyte i
                    for i in a to b step step {
                        result += i
                    }
                    poweroff = 1
                }
            }
        """)
        machine.assertMemory(0x020f, 25)
    }

    test("for bounds and step are evaluated once in source order") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &ubyte order = $0210
                &ubyte result = $0211

                sub side_from() -> ubyte {
                    order = 1
                    return 1
                }

                sub side_to() -> ubyte {
                    order = order * 10 + 2
                    return 5
                }

                sub side_step() -> byte {
                    order = order * 10 + 3
                    return 2
                }

                sub start() {
                    ubyte i
                    order = 0
                    for i in side_from() to side_to() step side_step() {
                        result += i
                    }
                    poweroff = 1
                }
            }
        """)
        machine.assertMemory(0x0210, 123)
        machine.assertMemory(0x0211, 9)
    }

    test("continue still performs the next step") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &ubyte count = $0212
                &ubyte result = $0213
                sub start() {
                    ubyte @shared step = 1
                    ubyte i
                    for i in 0 to 5 step step {
                        if i == 2
                            continue
                        count++
                        result += i
                    }
                    poweroff = 1
                }
            }
        """)
        machine.assertMemory(0x0212, 5)
        machine.assertMemory(0x0213, 13)
    }

    test("dynamic long compilation fails with an unsupported variable-step diagnostic") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                sub start() {
                    long i
                    for i in 0 to 10 step dynamicStep() {
                    }
                    poweroff = 1
                }

                sub dynamicStep() -> long {
                    return 1
                }
            }
        """.trimIndent()
        val errors = ErrorReporterForTests(throwExceptionAtReportIfErrors = false, keepMessagesAfterReporting = true)
        val failure = shouldThrow<AssemblyError> {
            compileText(Cx16Target(), false, src, outputDir, errors = errors)
        }
        (failure.message ?: errors.printedErrors.joinToString("\n")) shouldContain
            "variable-step for loops over long ranges are not supported"
        errors.printedErrors shouldBe listOf("\ninternal error")
    }
})
