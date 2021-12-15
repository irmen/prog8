package prog8tests

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import prog8.compiler.target.C64Target
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.assertSuccess
import prog8tests.helpers.compileText


class TestAstChecks: FunSpec({

    test("conditional expression w/float works even without tempvar to split it") {
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
        compileText(C64Target, true, text, writeAssembly = true, errors=errors).assertSuccess()
        errors.errors.size shouldBe 0
        errors.warnings.size shouldBe 2
        errors.warnings[0] shouldContain "converted to float"
        errors.warnings[1] shouldContain "converted to float"
    }
})
