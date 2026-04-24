package prog8tests.vm

import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.code.core.IErrorReporter
import prog8.code.core.Position
import prog8.code.target.VMTarget
import prog8.intermediate.IRFileReader
import prog8.vm.VmRunner
import prog8.vm.VmVariableAllocator
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText
import kotlin.io.path.readText

class TestDivmodFix : FunSpec({
    test("divmod does not crash when results are unused (optimization issue)") {
        val outputDir = tempdir().toPath()
        val src = """
            %zeropage basicsafe
            %option no_sysinit
            main {
                sub start() {
                    ubyte num = 230
                    ubyte div = 13
                    ubyte d, r = divmod(num, div)
                }
            }
        """.trimIndent()
        
        val innerErrors = ErrorReporterForTests(throwExceptionAtReportIfErrors = false, keepMessagesAfterReporting = true)
        val errors = object : IErrorReporter by innerErrors {
            override fun printSingleError(errormessage: String) {
                innerErrors.errors.add(errormessage)
            }
        }
        val result = try {
            compileText(VMTarget(), true, src, outputDir, writeAssembly = true, errors = errors)
        } catch (e: Exception) {
            fail("Compilation threw exception (unused): $e\n" + e.stackTraceToString() + "\nErrors:\n" + innerErrors.errors.joinToString("\n"))
        }
        if (result == null || innerErrors.errors.isNotEmpty()) {
            fail("Compilation failed (unused):\n" + innerErrors.errors.joinToString("\n"))
        }
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runProgram(virtfile.readText(), false)
    }

    test("divmod results are correct with optimizations") {
        val outputDir = tempdir().toPath()
        val src = """
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte d_res
                ubyte r_res
                sub start() {
                    d_res = 0
                    r_res = 0
                    ubyte num = 230
                    ubyte div = 13
                    ubyte d, r = divmod(num, div)
                    d_res = d
                    r_res = r
                }
            }
        """.trimIndent()
        
        val innerErrors = ErrorReporterForTests(throwExceptionAtReportIfErrors = false, keepMessagesAfterReporting = true)
        val errors = object : IErrorReporter by innerErrors {
            override fun printSingleError(errormessage: String) {
                innerErrors.errors.add(errormessage)
            }
        }
        val result = try {
            compileText(VMTarget(), true, src, outputDir, writeAssembly = true, errors = errors)
        } catch (e: Exception) {
            fail("Compilation threw exception (correct results): $e\n" + e.stackTraceToString() + "\nErrors:\n" + innerErrors.errors.joinToString("\n"))
        }
        if (result == null || innerErrors.errors.isNotEmpty()) {
            fail("Compilation failed (correct results):\n" + innerErrors.errors.joinToString("\n"))
        }

        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val irSource = virtfile.readText()
        val irProgram = IRFileReader().read(irSource)
        val allocations = VmVariableAllocator(irProgram.st, irProgram.encoding, irProgram.options.compTarget).allocations
        
        VmRunner().runAndTestProgram(irSource) { vm ->
            vm.memory.getUB(allocations["main.d_res"]!!.toUInt()) shouldBe 17u
            vm.memory.getUB(allocations["main.r_res"]!!.toUInt()) shouldBe 9u
        }
    }
})
